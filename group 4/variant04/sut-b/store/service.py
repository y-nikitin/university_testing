from dataclasses import replace
from .models import Order, OrderItem, OrderRequest, OrderStatus
from .ports import ProductRepository, OrderRepository, CouponValidator


class OrderRejectedError(Exception):
    pass


def integer(value: int, minimum: int, maximum: int, field: str) -> None:
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError("Некоректне значення: " + field)


class OrderService:
    def __init__(self, products: ProductRepository, orders: OrderRepository, coupons: CouponValidator):
        if products is None or orders is None or coupons is None:
            raise ValueError("Усі залежності мають бути задані")
        self.products, self.orders, self.coupons = products, orders, coupons

    def calculate_total(self, unit_price: int, quantity: int, coupon_valid: bool) -> int:
        integer(unit_price, 100, 1_000_000, "Ціна")
        integer(quantity, 1, 20, "Кількість")
        if type(coupon_valid) is not bool:
            raise ValueError("Ознака купона має бути логічною")
        subtotal = unit_price * quantity
        discount = 15 if subtotal >= 120_000 and coupon_valid else 0
        return subtotal - subtotal * discount // 100

    def place_order(self, request: OrderRequest) -> Order:
        if not isinstance(request, OrderRequest):
            raise ValueError("Некоректна заявка")
        if not isinstance(request.reference, str) or not request.reference.strip() or len(request.reference) > 40:
            raise ValueError("Код замовлення має містити 1–40 символів")
        integer(request.product_id, 1, 2**63 - 1, "Ідентифікатор товару")
        integer(request.quantity, 1, 20, "Кількість")
        if type(request.premium) is not bool or type(request.coupon_claimed) is not bool:
            raise ValueError("Ознаки мають бути логічними")
        product = self.products.find_by_id(request.product_id)
        if product is None:
            raise ValueError("Товар не знайдено")
        limit = 8 if request.premium else 4
        if request.quantity > limit:
            raise OrderRejectedError("Перевищено ліміт кількості")
        if request.quantity > product.stock:
            raise OrderRejectedError("Недостатньо товару")
        coupon = False
        if request.coupon_claimed:
            coupon = self.coupons.is_valid(product, request.quantity)
            if type(coupon) is not bool:
                raise RuntimeError("Некоректна відповідь перевірки купона")
        total = self.calculate_total(product.unit_price, request.quantity, coupon)
        item = OrderItem(0, product.id, request.quantity, product.unit_price, product.on_sale)
        return self.orders.create(Order(0, request.reference, request.premium, coupon,
                                        total, OrderStatus.CONFIRMED, item))

    def return_order(self, order_id: int, days_since_purchase: int, packaging_open: bool = False) -> Order:
        integer(order_id, 1, 2**63 - 1, "Ідентифікатор замовлення")
        integer(days_since_purchase, 0, 36500, "Дні після покупки")
        if type(packaging_open) is not bool:
            raise ValueError("Ознака пакування має бути логічною")
        order = self.orders.find_by_id(order_id)
        if order is None:
            raise ValueError("Замовлення не знайдено")
        if order.status == OrderStatus.RETURNED:
            raise OrderRejectedError("Замовлення вже повернено")
        if days_since_purchase > 7 or order.item.on_sale:
            raise OrderRejectedError("Повернення не дозволене")
        self.orders.update_status(order_id, OrderStatus.RETURNED)
        return replace(order, status=OrderStatus.RETURNED)
