from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    travelers: int
    days_before: int
    premium: bool
    flexible: bool


@dataclass(frozen=True)
class Result:
    status: str
    travelers: int = 0
    discount_percent: int = 0
    options: int = 0
    late_fee: int = 0
    total: int = 0


class RoutePolicy(Protocol):
    def is_open(self) -> bool:
        ...

class TravelCapacity(Protocol):
    def free_seats(self) -> int:
        ...

class TravelRepository(Protocol):
    def save(self, request: Request, result: Result) -> None:
        """Фіксує всю операцію або повідомляє про помилку без часткових змін."""
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


class TravelBookingService:
    def __init__(self, route: RoutePolicy, capacity: TravelCapacity, bookings: TravelRepository) -> None:
        if route is None or capacity is None or bookings is None:
            raise ValueError("Усі залежності мають бути задані")
        self._route = route
        self._capacity = capacity
        self._bookings = bookings

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.travelers, 1, 6, "Кількість туристів")
        _range(request.days_before, 1, 179, "Дні до виїзду")
        if type(request.premium) is not bool:
            raise ValueError("Недопустима логічна ознака: premium")
        if type(request.flexible) is not bool:
            raise ValueError("Недопустима логічна ознака: flexible")
        if not _flag(self._route.is_open(), "Відкритість напрямку"):
            return Result("Напрямок закритий")
        seats = _count(self._capacity.free_seats(), "Вільні місця")
        if seats < request.travelers:
            return Result("Недостатньо місць")
        if request.premium and request.flexible:
            option_rate = 45_000
        elif request.premium:
            option_rate = 25_000
        elif request.flexible:
            option_rate = 20_000
        else:
            option_rate = 0
        base = request.travelers * 100_000
        discount = 10 if request.days_before > 60 else 0
        options = request.travelers * option_rate
        late_fee = request.travelers * 15_000 if request.days_before < 7 else 0
        total = base - base * discount // 100 + options + late_fee
        result = Result("Заброньовано", request.travelers, discount, options, late_fee, total)
        self._bookings.save(request, result)
        return result
