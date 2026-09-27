from dataclasses import replace
from datetime import date
from .models import Room, Booking, BookingItem, BookingStatus
from .service import integer, BookingRejectedError

class PostgresRoomRepository:
    def __init__(self, connection):
        self.connection = connection

    def create(self, room):
        with self.connection.cursor() as c:
            c.execute("INSERT INTO rooms(code,nightly_rate,capacity) VALUES (%s,%s,%s) RETURNING id", (room.code,room.nightly_rate,room.capacity))
            return replace(room, id=c.fetchone()[0])

    def find_by_id(self, room_id):
        with self.connection.cursor() as c:
            c.execute("SELECT id,code,nightly_rate,capacity FROM rooms WHERE id=%s", (room_id,))
            row = c.fetchone()
            return Room(*row) if row else None

    def update(self, room):
        with self.connection.cursor() as c:
            c.execute("UPDATE rooms SET code=%s,nightly_rate=%s,capacity=%s WHERE id=%s", (room.code,room.nightly_rate,room.capacity,room.id))
            if c.rowcount == 0:
                raise ValueError("Номер не знайдено")

    def delete(self, room_id):
        with self.connection.cursor() as c:
            c.execute("DELETE FROM rooms WHERE id=%s", (room_id,))

class PostgresBookingRepository:
    _select = ("SELECT b.id,b.reference,b.check_in,b.check_out,b.guests,b.regular,b.total,b.prepaid,b.refund,b.status,"
               "i.id,i.room_id,i.nightly_rate FROM bookings b JOIN booking_items i ON i.booking_id=b.id ")

    def __init__(self, connection):
        self.connection = connection

    def create(self, booking):
        with self.connection.transaction():
            with self.connection.cursor() as c:
                c.execute("INSERT INTO bookings(reference,check_in,check_out,guests,regular,total,prepaid,refund,status) "
                          "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s) RETURNING id",
                          (booking.reference,booking.check_in,booking.check_out,booking.guests,booking.regular,booking.total,booking.prepaid,booking.refund,booking.status.value))
                booking_id = c.fetchone()[0]
                c.execute("INSERT INTO booking_items(booking_id,room_id,nightly_rate) VALUES (%s,%s,%s) RETURNING id",
                          (booking_id,booking.item.room_id,booking.item.nightly_rate))
                saved = replace(booking, id=booking_id, item=replace(booking.item, id=c.fetchone()[0]))
        return saved

    @staticmethod
    def _map(row):
        return Booking(*row[:9], BookingStatus(row[9]), BookingItem(*row[10:]))

    def find_by_id(self, booking_id):
        with self.connection.cursor() as c:
            c.execute(self._select + "WHERE b.id=%s", (booking_id,))
            row = c.fetchone()
            return self._map(row) if row else None

    def find_bookings(self, room_id, status):
        integer(room_id, 1, 2**63 - 1)
        if not isinstance(status, BookingStatus):
            raise ValueError("Некоректний статус")
        with self.connection.cursor() as c:
            c.execute(self._select + "WHERE i.room_id=%s AND b.status=%s ORDER BY b.id", (room_id,status.value))
            return [self._map(row) for row in c.fetchall()]

    def is_available(self, room_id, check_in, check_out):
        integer(room_id, 1, 2**63 - 1)
        if type(check_in) is not date or type(check_out) is not date or not 1 <= (check_out-check_in).days <= 30:
            raise ValueError("Некоректний період")
        with self.connection.cursor() as c:
            c.execute("SELECT NOT EXISTS (SELECT 1 FROM bookings b JOIN booking_items i ON i.booking_id=b.id "
                      "WHERE i.room_id=%s AND b.status='CONFIRMED' AND b.check_in < %s AND b.check_out > %s)",
                      (room_id,check_out,check_in))
            return c.fetchone()[0]

    def cancel(self, booking_id, refund):
        integer(refund, 0, 30000000)
        with self.connection.cursor() as c:
            c.execute("UPDATE bookings SET status='CANCELLED',refund=%s WHERE id=%s AND status='CONFIRMED'", (refund,booking_id))
            if c.rowcount == 0:
                raise BookingRejectedError("Підтверджене бронювання не знайдено")

    def delete(self, booking_id):
        with self.connection.cursor() as c:
            c.execute("DELETE FROM bookings WHERE id=%s", (booking_id,))
