from dataclasses import replace
from .models import Product, Order, OrderItem, OrderStatus


class PostgresProductRepository:
    def __init__(self, connection):
        self.connection = connection

    def create(self, sku: str, unit_price: int, stock: int, on_sale: bool) -> Product:
        with self.connection.cursor() as cursor:
            cursor.execute("INSERT INTO products(sku, unit_price, stock, on_sale) VALUES (%s,%s,%s,%s) "
                           "RETURNING id, sku, unit_price, stock, on_sale", (sku, unit_price, stock, on_sale))
            return Product(*cursor.fetchone())

    def find_by_id(self, product_id: int) -> Product | None:
        with self.connection.cursor() as cursor:
            cursor.execute("SELECT id, sku, unit_price, stock, on_sale FROM products WHERE id=%s", (product_id,))
            row = cursor.fetchone()
            return Product(*row) if row else None

    def update(self, product: Product) -> None:
        with self.connection.cursor() as cursor:
            cursor.execute("UPDATE products SET sku=%s, unit_price=%s, stock=%s, on_sale=%s WHERE id=%s",
                           (product.sku, product.unit_price, product.stock, product.on_sale, product.id))
            if cursor.rowcount == 0:
                raise ValueError("Товар не знайдено")

    def delete(self, product_id: int) -> None:
        with self.connection.cursor() as cursor:
            cursor.execute("DELETE FROM products WHERE id=%s", (product_id,))


class PostgresOrderRepository:
    _select = ("SELECT o.id, o.reference, o.premium, o.coupon_valid, o.total, o.status, "
               "i.id, i.product_id, i.quantity, i.unit_price, i.on_sale "
               "FROM orders o JOIN order_items i ON i.order_id=o.id ")

    def __init__(self, connection):
        self.connection = connection

    def create(self, order: Order) -> Order:
        # Замовлення та його єдина позиція зберігаються разом.
        with self.connection.transaction():
            with self.connection.cursor() as cursor:
                cursor.execute("INSERT INTO orders(reference,premium,coupon_valid,total,status) "
                               "VALUES (%s,%s,%s,%s,%s) RETURNING id",
                               (order.reference, order.premium, order.coupon_valid, order.total, order.status.value))
                order_id = cursor.fetchone()[0]
                item = order.item
                cursor.execute("INSERT INTO order_items(order_id,product_id,quantity,unit_price,on_sale) "
                               "VALUES (%s,%s,%s,%s,%s) RETURNING id",
                               (order_id, item.product_id, item.quantity, item.unit_price, item.on_sale))
                item_id = cursor.fetchone()[0]
        return replace(order, id=order_id, item=replace(item, id=item_id))

    @staticmethod
    def _map(row) -> Order:
        return Order(*row[:5], OrderStatus(row[5]), OrderItem(*row[6:]))

    def find_by_id(self, order_id: int) -> Order | None:
        with self.connection.cursor() as cursor:
            cursor.execute(self._select + "WHERE o.id=%s", (order_id,))
            row = cursor.fetchone()
            return self._map(row) if row else None

    def find_all(self) -> list[Order]:
        with self.connection.cursor() as cursor:
            cursor.execute(self._select + "ORDER BY o.id")
            return [self._map(row) for row in cursor.fetchall()]

    def find_orders(self, product_id: int, status: OrderStatus) -> list[Order]:
        if type(product_id) is not int or product_id <= 0 or product_id > 2**63 - 1 or not isinstance(status, OrderStatus):
            raise ValueError("Некоректні аргументи фільтра")
        with self.connection.cursor() as cursor:
            cursor.execute(self._select + "WHERE i.product_id=%s AND o.status=%s ORDER BY o.id",
                           (product_id, status.value))
            return [self._map(row) for row in cursor.fetchall()]

    def update_status(self, order_id: int, status: OrderStatus) -> None:
        if not isinstance(status, OrderStatus):
            raise ValueError("Некоректний статус")
        with self.connection.cursor() as cursor:
            cursor.execute("UPDATE orders SET status=%s WHERE id=%s", (status.value, order_id))
            if cursor.rowcount == 0:
                raise ValueError("Замовлення не знайдено")

    def delete(self, order_id: int) -> None:
        with self.connection.cursor() as cursor:
            cursor.execute("DELETE FROM orders WHERE id=%s", (order_id,))
