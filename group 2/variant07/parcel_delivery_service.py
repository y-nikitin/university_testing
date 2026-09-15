from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    weight_grams: int
    distance_km: int
    express: bool
    insured: bool


@dataclass(frozen=True)
class Result:
    status: str
    billable_kilograms: int = 0
    base_price: int = 0
    distance_fee: int = 0
    options_fee: int = 0
    total: int = 0


class RouteCapacity(Protocol):
    def available_grams(self) -> int:
        ...


class ExpressPolicy(Protocol):
    def is_supported(self) -> bool:
        ...


class ShipmentRepository(Protocol):
    def save(self, request: Request, result: Result) -> None:
        ...


def _range(value: int, minimum: int, maximum: int, field: str) -> None:
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError("Недопустиме значення: " + field)


def _flag(value: bool, field: str) -> bool:
    if type(value) is not bool:
        raise RuntimeError("Некоректні відомості: " + field)
    return value


def _count(value: int, field: str) -> int:
    if type(value) is not int or value < 0:
        raise RuntimeError("Некоректні відомості: " + field)
    return value


class ParcelDeliveryService:
    def __init__(self, capacity: RouteCapacity, express: ExpressPolicy, shipments: ShipmentRepository) -> None:
        if capacity is None or express is None or shipments is None:
            raise ValueError("Усі залежності мають бути задані")
        self._capacity = capacity
        self._express = express
        self._shipments = shipments

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.weight_grams, 1, 29_999, "Вага посилки")
        _range(request.distance_km, 1, 3_000, "Відстань")
        if type(request.express) is not bool or type(request.insured) is not bool:
            raise ValueError("Ознаки мають бути логічними значеннями")
        capacity = _count(self._capacity.available_grams(), "Доступна вантажність")
        if capacity < request.weight_grams:
            return Result("Недостатня вантажність")
        if request.express and not _flag(self._express.is_supported(), "Доступність експрес-доставки"):
            return Result("Експрес-доставка недоступна")
        kilograms = (request.weight_grams + 999) // 1_000
        base_price = 4_000 + kilograms * 1_000
        distance_fee = 3_000 if request.distance_km >= 500 else 0
        if request.express and request.insured:
            options = 6_000
        elif request.express:
            options = 4_000
        elif request.insured:
            options = 2_000
        else:
            options = 0
        result = Result("Прийнято", kilograms, base_price, distance_fee, options,
                        base_price + distance_fee + options)
        self._shipments.save(request, result)
        return result
