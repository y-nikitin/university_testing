from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    nights: int
    guests: int
    loyalty_claimed: bool
    breakfast: bool


@dataclass(frozen=True)
class Result:
    status: str
    nights: int = 0
    guests: int = 0
    discount_percent: int = 0
    accommodation: int = 0
    breakfast_cost: int = 0
    total: int = 0


class RoomAvailability(Protocol):
    def is_available(self) -> bool:
        ...


class LoyaltyProvider(Protocol):
    def is_member(self) -> bool:
        ...


class BookingRepository(Protocol):
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


class BookingService:
    def __init__(self, rooms: RoomAvailability, loyalty: LoyaltyProvider, bookings: BookingRepository) -> None:
        if rooms is None or loyalty is None or bookings is None:
            raise ValueError("Усі залежності мають бути задані")
        self._rooms = rooms
        self._loyalty = loyalty
        self._bookings = bookings

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.nights, 1, 29, "Кількість ночей")
        _range(request.guests, 1, 4, "Кількість гостей")
        if type(request.loyalty_claimed) is not bool or type(request.breakfast) is not bool:
            raise ValueError("Ознаки мають бути логічними значеннями")
        if not _flag(self._rooms.is_available(), "Доступність номера"):
            return Result("Немає номера")
        member = request.loyalty_claimed and _flag(self._loyalty.is_member(), "Участь у програмі лояльності")
        long_stay = request.nights > 7
        if member and long_stay:
            discount = 15
        elif member:
            discount = 10
        elif long_stay:
            discount = 5
        else:
            discount = 0
        base = request.nights * 120_000
        accommodation = base - base * discount // 100
        breakfast = request.nights * min(request.guests, 3) * 15_000 if request.breakfast else 0
        result = Result("Підтверджено", request.nights, request.guests, discount,
                        accommodation, breakfast, accommodation + breakfast)
        self._bookings.save(request, result)
        return result
