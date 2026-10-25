import os
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from uuid import uuid4

import psycopg
import pytest
from testcontainers.postgres import PostgresContainer
from rental.models import Equipment, RentalRequest, RentalStatus as S
from rental.repositories import PostgresEquipmentRepository, PostgresRentalRepository
from rental.service import RentalService, RentalRejectedError

rental_id = 0

class Policy:
    def is_allowed(self, equipment, quantity, days):
        return True

@pytest.fixture(scope="module")
def sut():
    with PostgresContainer("postgres:17.6-alpine") as postgres:
        options = dict(host=postgres.get_container_host_ip(), port=postgres.get_exposed_port(5432),
                       user=postgres.username, password=postgres.password, dbname=postgres.dbname, autocommit=True)
        with psycopg.connect(**options) as db:
            migration = Path(__file__).resolve().parents[1] / "migrations/V1__create_tables.sql"
            db.execute(migration.read_text(encoding="utf-8"))
            equipment = PostgresEquipmentRepository(db)
            rentals = PostgresRentalRepository(db)
            yield db, equipment, rentals, RentalService(equipment, rentals, Policy()), options

def item(sut):
    return sut[1].create(Equipment(0, str(uuid4()), 10000, 5))

def request(equipment):
    return RentalRequest(str(uuid4()), equipment.id, 1, 2, False)

def test_create_rental(sut):
    global rental_id
    saved = sut[3].rent(request(item(sut)))
    rental_id = saved.id
    assert saved.status == S.ACTIVE
    assert sut[2].find_by_id(saved.id) == saved

def test_return_rental(sut):
    returned = sut[3].return_rental(rental_id, 0)
    assert returned.status == S.RETURNED
    assert returned.late_fee == 0
    assert sut[2].find_by_id(rental_id) == returned

def test_equipment_stock(sut):
    saved = sut[1].create(Equipment(0, "REGRESSION-EQUIPMENT", 10000, 5))
    assert sut[1].find_by_id(saved.id).stock == 5

def test_equipment_rate(sut):
    saved = sut[1].create(Equipment(0, "REGRESSION-EQUIPMENT", 15000, 3))
    assert sut[1].find_by_id(saved.id).daily_rate == 15000

def test_rental_after_policy_check(sut):
    class DelayedPolicy:
        def is_allowed(self, equipment, quantity, days):
            time.sleep(float(os.getenv("REGRESSION_POLICY_DELAY_MS", "200")) / 1000)
            return True

    equipment = item(sut)
    data = request(equipment)
    def rent():
        with psycopg.connect(**sut[4]) as db:
            service = RentalService(PostgresEquipmentRepository(db), PostgresRentalRepository(db), DelayedPolicy())
            return service.rent(data)

    with ThreadPoolExecutor(max_workers=1) as executor:
        operation = executor.submit(rent)
        time.sleep(0.05)
        saved = sut[2].find_rentals(equipment.id, S.ACTIVE)
        assert len(saved) == 1
        assert saved[0].reference == data.reference
        assert saved[0].total == 20000

def test_rental_selection(sut):
    equipment = item(sut)
    prefix = str(uuid4())[:8]
    references = [prefix + "-A", prefix + "-B"]
    fixture_order = list(reversed(references)) if os.getenv("REGRESSION_REVERSE_FIXTURE") == "true" else references
    for reference in fixture_order:
        sut[3].rent(RentalRequest(reference, equipment.id, 1, 2, False))
    actual = [row[0] for row in sut[0].execute(
        "SELECT r.reference FROM rentals r JOIN rental_items i ON i.rental_id=r.id WHERE i.equipment_id=%s", (equipment.id,))]
    assert actual == references

def test_rental_amount(sut):
    equipment = item(sut)
    saved = sut[3].rent(request(equipment))
    stored = sut[2].find_by_id(saved.id)
    assert stored.total == 20000
    assert stored.item.equipment_id == equipment.id
    assert stored.item.quantity == 1
    assert stored.item.daily_rate == 10000

def test_stock_rejection(sut):
    equipment = item(sut)
    with pytest.raises(RentalRejectedError):
        sut[3].rent(RentalRequest(str(uuid4()), equipment.id, 6, 2, False))
    assert sut[2].find_rentals(equipment.id, S.ACTIVE) == []

def test_policy_rejection(sut):
    class DeniedPolicy:
        def is_allowed(self, equipment, quantity, days):
            return False

    equipment = item(sut)
    denied = RentalService(sut[1], sut[2], DeniedPolicy())
    with pytest.raises(RentalRejectedError):
        denied.rent(request(equipment))
    assert sut[2].find_rentals(equipment.id, S.ACTIVE) == []

def test_repeated_return(sut):
    saved = sut[3].rent(request(item(sut)))
    returned = sut[3].return_rental(saved.id, 0)
    with pytest.raises(RentalRejectedError):
        sut[3].return_rental(saved.id, 0)
    assert sut[2].find_by_id(saved.id) == returned
