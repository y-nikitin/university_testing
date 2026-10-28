from dataclasses import replace
from .models import Equipment, RentalItem, RentalOrder, RentalStatus
from .service import RentalRejectedError, integer

class PostgresEquipmentRepository:
    def __init__(self, connection):
        self.connection = connection

    def create(self, equipment):
        with self.connection.cursor() as cursor:
            cursor.execute("INSERT INTO equipment(code,daily_rate,stock) VALUES (%s,%s,%s) RETURNING id", (equipment.code,equipment.daily_rate,equipment.stock))
            return replace(equipment, id=cursor.fetchone()[0])

    def find_by_id(self, equipment_id):
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT id,code,daily_rate,stock FROM equipment WHERE id=%s", (equipment_id,))
            row = cursor.fetchone()
            return Equipment(*row) if row else None

    def update(self, equipment):
        with self.connection.cursor() as cursor:
            cursor.execute("UPDATE equipment SET code=%s,daily_rate=%s,stock=%s WHERE id=%s", (equipment.code,equipment.daily_rate,equipment.stock,equipment.id))
            if cursor.rowcount == 0:
                raise ValueError("Обладнання не знайдено")

    def delete(self, equipment_id):
        with self.connection.cursor() as cursor:
            cursor.execute("DELETE FROM equipment WHERE id=%s", (equipment_id,))

class PostgresRentalRepository:
    _select = ("SELECT r.id,r.reference,r.days,r.premium,r.total,r.late_fee,r.status,i.id,i.equipment_id,i.quantity,i.daily_rate "
               "FROM rentals r JOIN rental_items i ON i.rental_id=r.id ")

    def __init__(self, connection):
        self.connection = connection

    def create(self, rental):
        with self.connection.transaction():
            with self.connection.cursor() as cursor:
                cursor.execute("INSERT INTO rentals(reference,days,premium,total,late_fee,status) VALUES (%s,%s,%s,%s,%s,%s) RETURNING id",
                               (rental.reference,rental.days,rental.premium,rental.total,rental.late_fee,rental.status.value))
                rental_id = cursor.fetchone()[0]
                item = rental.item
                cursor.execute("INSERT INTO rental_items(rental_id,equipment_id,quantity,daily_rate) VALUES (%s,%s,%s,%s) RETURNING id",
                               (rental_id,item.equipment_id,item.quantity,item.daily_rate))
                item_id = cursor.fetchone()[0]
                saved = replace(rental, id=rental_id, item=replace(item, id=item_id))
        return saved

    @staticmethod
    def _map(row):
        return RentalOrder(*row[:6], RentalStatus(row[6]), RentalItem(*row[7:]))

    def find_by_id(self, rental_id):
        with self.connection.cursor() as cursor:
            cursor.execute(self._select + "WHERE r.id=%s", (rental_id,))
            row = cursor.fetchone()
            return self._map(row) if row else None

    def find_rentals(self, equipment_id, status):
        integer(equipment_id, 1, 2**63 - 1)
        if not isinstance(status, RentalStatus):
            raise ValueError("Некоректний статус")
        with self.connection.cursor() as cursor:
            cursor.execute(self._select + "WHERE i.equipment_id=%s AND r.status=%s ORDER BY r.id", (equipment_id,status.value))
            return [self._map(row) for row in cursor.fetchall()]

    def mark_returned(self, rental_id, late_fee):
        integer(late_fee, 0, 2**63 - 1)
        with self.connection.cursor() as cursor:
            cursor.execute("UPDATE rentals SET status='Returned',late_fee=%s WHERE id=%s AND status='Active'", (late_fee,rental_id))
            if cursor.rowcount == 0:
                raise RentalRejectedError("Активну оренду не знайдено")

    def delete(self, rental_id):
        with self.connection.cursor() as cursor:
            cursor.execute("DELETE FROM rentals WHERE id=%s", (rental_id,))
