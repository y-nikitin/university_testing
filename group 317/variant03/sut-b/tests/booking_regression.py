import os
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import date, timedelta
from pathlib import Path
from uuid import uuid4

import psycopg
import pytest
from testcontainers.postgres import PostgresContainer
from hotel.models import Room, BookingRequest, BookingStatus as S
from hotel.repositories import PostgresRoomRepository, PostgresBookingRepository
from hotel.service import BookingService, BookingRejectedError

booking_id = 0

class Policy:
    def is_allowed(self, room, guests, nights):
        return True

@pytest.fixture(scope="module")
def sut():
    with PostgresContainer("postgres:17.6-alpine") as postgres:
        options = dict(host=postgres.get_container_host_ip(), port=postgres.get_exposed_port(5432),
                       user=postgres.username, password=postgres.password, dbname=postgres.dbname, autocommit=True)
        with psycopg.connect(**options) as db:
            migrations = Path(__file__).resolve().parents[1] / "migrations"
            for migration in sorted(migrations.glob("V*__*.sql")):
                db.execute(migration.read_text(encoding="utf-8"))
            rooms = PostgresRoomRepository(db)
            bookings = PostgresBookingRepository(db)
            yield db, rooms, bookings, BookingService(rooms, bookings, Policy()), options

def room(sut):
    return sut[1].create(Room(0, str(uuid4()), 10000, 2))

def request(room):
    return BookingRequest(str(uuid4()), room.id, date(2030, 6, 1), date(2030, 6, 3), 2, False)

def test_create_booking(sut):
    global booking_id
    saved = sut[3].book(request(room(sut)))
    booking_id = saved.id
    assert saved.status == S.CONFIRMED
    assert sut[2].find_by_id(saved.id) == saved

def test_cancel_booking(sut):
    cancelled = sut[3].cancel_booking(booking_id, 30)
    assert cancelled.status == S.CANCELLED
    assert sut[2].find_by_id(booking_id) == cancelled

def test_room_capacity(sut):
    saved = sut[1].create(Room(0, "REGRESSION-ROOM", 10000, 2))
    assert sut[1].find_by_id(saved.id).capacity == 2

def test_room_rate(sut):
    saved = sut[1].create(Room(0, "REGRESSION-ROOM", 15000, 3))
    assert sut[1].find_by_id(saved.id).nightly_rate == 15000

def test_booking_after_policy_check(sut):
    class DelayedPolicy:
        def is_allowed(self, room, guests, nights):
            time.sleep(float(os.getenv("REGRESSION_POLICY_DELAY_MS", "200")) / 1000)
            return True

    selected = room(sut)
    data = request(selected)
    def book():
        with psycopg.connect(**sut[4]) as db:
            service = BookingService(PostgresRoomRepository(db), PostgresBookingRepository(db), DelayedPolicy())
            return service.book(data)

    with ThreadPoolExecutor(max_workers=1) as executor:
        operation = executor.submit(book)
        time.sleep(0.05)
        saved = sut[2].find_bookings(selected.id, S.CONFIRMED)
        assert len(saved) == 1
        assert saved[0].reference == data.reference
        assert saved[0].total == 20000

def test_booking_selection(sut):
    selected = room(sut)
    prefix = str(uuid4())[:8]
    references = [prefix + "-A", prefix + "-B"]
    fixtures = [(references[0], date(2030, 6, 1)), (references[1], date(2030, 6, 10))]
    fixture_order = list(reversed(fixtures)) if os.getenv("REGRESSION_REVERSE_FIXTURE") == "true" else fixtures
    for reference, check_in in fixture_order:
        sut[3].book(BookingRequest(reference, selected.id, check_in, check_in + timedelta(days=2), 2, False))
    actual = [row[0] for row in sut[0].execute(
        "SELECT b.reference FROM bookings b JOIN booking_items i ON i.booking_id=b.id WHERE i.room_id=%s", (selected.id,))]
    assert actual == references

def test_booking_amount(sut):
    selected = room(sut)
    saved = sut[3].book(request(selected))
    stored = sut[2].find_by_id(saved.id)
    assert stored.total == 20000
    assert stored.item.room_id == selected.id
    assert stored.item.nightly_rate == 10000
    assert (stored.check_out - stored.check_in).days == 2

def test_capacity_rejection(sut):
    selected = room(sut)
    data = BookingRequest(str(uuid4()), selected.id, date(2030, 6, 1), date(2030, 6, 3), 3, False)
    with pytest.raises(BookingRejectedError):
        sut[3].book(data)
    assert sut[2].find_bookings(selected.id, S.CONFIRMED) == []

def test_policy_rejection(sut):
    class DeniedPolicy:
        def is_allowed(self, room, guests, nights):
            return False

    selected = room(sut)
    denied = BookingService(sut[1], sut[2], DeniedPolicy())
    with pytest.raises(BookingRejectedError):
        denied.book(request(selected))
    assert sut[2].find_bookings(selected.id, S.CONFIRMED) == []

def test_repeated_cancellation(sut):
    saved = sut[3].book(request(room(sut)))
    cancelled = sut[3].cancel_booking(saved.id, 30)
    with pytest.raises(BookingRejectedError):
        sut[3].cancel_booking(saved.id, 30)
    assert sut[2].find_by_id(saved.id) == cancelled
