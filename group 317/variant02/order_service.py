from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    quantity: int
    unit_price: int
    premium: bool
    coupon_claimed: bool


@dataclass(frozen=True)
class Result:
    status: str
    quantity: int = 0
    discount_percent: int = 0
    goods: int = 0
    delivery: int = 0
    total: int = 0


class StockProvider(Protocol):
    def available_units(self) -> int:
        ...


class CouponValidator(Protocol):
    def is_valid(self) -> bool:
        ...


class OrderRepository(Protocol):
    def save(self, request: Request, result: Result) -> None:
        ...


def _range(value: int, minimum: int, maximum: int, field: str) -> None:
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError("Недопустиме значення: " + field)


def _count(value: int, field: str) -> int:
    if type(value) is not int or value < 0:
        raise RuntimeError("Некоректні відомості: " + field)
    return value


def _flag(value: bool, field: str) -> bool:
    if type(value) is not bool:
        raise RuntimeError("Некоректні відомості: " + field)
    return value


class OrderService:
    def __init__(self, stock: StockProvider, coupons: CouponValidator, orders: OrderRepository) -> None:
        if stock is None or coupons is None or orders is None:
            raise ValueError("Усі залежності мають бути задані")
        self._stock = stock
        self._coupons = coupons
        self._orders = orders

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.quantity, 1, 19, "Кількість товару")
        _range(request.unit_price, 100, 100_000, "Ціна одиниці")
        if type(request.premium) is not bool or type(request.coupon_claimed) is not bool:
            raise ValueError("Ознаки мають бути логічними значеннями")
        available = _count(self._stock.available_units(), "Залишок товару")
        if available < request.quantity:
            return Result("Недостатньо товару")
        coupon = request.coupon_claimed and _flag(self._coupons.is_valid(), "Чинність купона")
        if request.premium and coupon:
            discount = 15
        elif request.premium:
            discount = 10
        elif coupon:
            discount = 5
        else:
            discount = 0
        subtotal = request.quantity * request.unit_price
        goods = subtotal - subtotal * discount // 100
        delivery = 0 if goods > 50_000 else 5_000
        result = Result("Оформлено", request.quantity, discount, goods, delivery, goods + delivery)
        self._orders.save(request, result)
        return result
