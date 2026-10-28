from dataclasses import replace
from .models import Customer, Parcel, Shipment, ShipmentStatus
from .service import integer, ShipmentRejectedError

class PostgresCustomerRepository:
    def __init__(self, connection):
        self.connection = connection

    def create(self, customer):
        with self.connection.cursor() as c:
            c.execute("INSERT INTO customers(code,regular) VALUES (%s,%s) RETURNING id", (customer.code,customer.regular))
            return replace(customer, id=c.fetchone()[0])

    def find_by_id(self, customer_id):
        with self.connection.cursor() as c:
            c.execute("SELECT id,code,regular FROM customers WHERE id=%s", (customer_id,))
            row = c.fetchone()
            return Customer(*row) if row else None

    def update(self, customer):
        with self.connection.cursor() as c:
            c.execute("UPDATE customers SET code=%s,regular=%s WHERE id=%s", (customer.code,customer.regular,customer.id))
            if c.rowcount == 0:
                raise ValueError("Клієнта не знайдено")

    def delete(self, customer_id):
        with self.connection.cursor() as c:
            c.execute("DELETE FROM customers WHERE id=%s", (customer_id,))

class PostgresParcelRepository:
    def __init__(self, connection):
        self.connection = connection

    def create(self, parcel):
        with self.connection.cursor() as c:
            c.execute("INSERT INTO parcels(code,weight_grams,declared_value) VALUES (%s,%s,%s) RETURNING id", (parcel.code,parcel.weight_grams,parcel.declared_value))
            return replace(parcel, id=c.fetchone()[0])

    def find_by_id(self, parcel_id):
        with self.connection.cursor() as c:
            c.execute("SELECT id,code,weight_grams,declared_value FROM parcels WHERE id=%s", (parcel_id,))
            row = c.fetchone()
            return Parcel(*row) if row else None

    def update(self, parcel):
        with self.connection.cursor() as c:
            c.execute("UPDATE parcels SET code=%s,weight_grams=%s,declared_value=%s WHERE id=%s", (parcel.code,parcel.weight_grams,parcel.declared_value,parcel.id))
            if c.rowcount == 0:
                raise ValueError("Посилку не знайдено")

    def delete(self, parcel_id):
        with self.connection.cursor() as c:
            c.execute("DELETE FROM parcels WHERE id=%s", (parcel_id,))

class PostgresShipmentRepository:
    _select = ("SELECT id,reference,customer_id,parcel_id,distance_km,urgent,pickup,regular,coupon_valid,"
               "weight_grams,declared_value,base_fee,adjustment,total,status FROM shipments ")

    def __init__(self, connection):
        self.connection = connection

    def create(self, s):
        with self.connection.cursor() as c:
            c.execute("INSERT INTO shipments(reference,customer_id,parcel_id,distance_km,urgent,pickup,regular,coupon_valid,"
                      "weight_grams,declared_value,base_fee,adjustment,total,status) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s) RETURNING id",
                      (s.reference,s.customer_id,s.parcel_id,s.distance_km,s.urgent,s.pickup,s.regular,s.coupon_valid,
                       s.weight_grams,s.declared_value,s.base_fee,s.adjustment,s.total,s.status.value))
            return replace(s, id=c.fetchone()[0])

    @staticmethod
    def _map(row):
        return Shipment(*row[:14], ShipmentStatus(row[14]))

    def find_by_id(self, shipment_id):
        with self.connection.cursor() as c:
            c.execute(self._select + "WHERE id=%s", (shipment_id,))
            row = c.fetchone()
            return self._map(row) if row else None

    def find_shipments(self, customer_id, status):
        integer(customer_id, 1, 2**63 - 1)
        if not isinstance(status, ShipmentStatus):
            raise ValueError("Некоректний статус")
        with self.connection.cursor() as c:
            c.execute(self._select + "WHERE customer_id=%s AND status=%s ORDER BY id", (customer_id,status.value))
            return [self._map(row) for row in c.fetchall()]

    def update_status(self, shipment_id, expected, next_status):
        if not isinstance(expected, ShipmentStatus) or not isinstance(next_status, ShipmentStatus):
            raise ValueError("Некоректний статус")
        if (expected, next_status) not in {(ShipmentStatus.REGISTERED, ShipmentStatus.DISPATCHED),
                                         (ShipmentStatus.REGISTERED, ShipmentStatus.CANCELLED),
                                         (ShipmentStatus.DISPATCHED, ShipmentStatus.DELIVERED)}:
            raise ValueError("Перехід статусу заборонений")
        with self.connection.cursor() as c:
            c.execute("UPDATE shipments SET status=%s WHERE id=%s AND status=%s", (next_status.value,shipment_id,expected.value))
            if c.rowcount == 0:
                raise ShipmentRejectedError("Відправлення відсутнє або його статус уже змінено")

    def delete(self, shipment_id):
        with self.connection.cursor() as c:
            c.execute("DELETE FROM shipments WHERE id=%s", (shipment_id,))
