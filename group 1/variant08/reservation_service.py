from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    guests: int
    hours_before: int
    terrace: bool
    celebration: bool


@dataclass(frozen=True)
class Result:
    status: str
    guests: int = 0
    zone: str = ""
    deposit: int = 0


class TerraceAvailability(Protocol):
    def is_open(self) -> bool:
        ...

class SeatAvailability(Protocol):
    def free_seats(self) -> int:
        ...

class ReservationRepository(Protocol):
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


class ReservationService:
    def __init__(self, terrace: TerraceAvailability, seats: SeatAvailability, reservations: ReservationRepository) -> None:
        if terrace is None or seats is None or reservations is None:
            raise ValueError("Усі залежності мають бути задані")
        self._terrace = terrace
        self._seats = seats
        self._reservations = reservations

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.guests, 1, 11, "Кількість гостей")
        _range(request.hours_before, 2, 720, "Години до візиту")
        if type(request.terrace) is not bool:
            raise ValueError("Недопустима логічна ознака: terrace")
        if type(request.celebration) is not bool:
            raise ValueError("Недопустима логічна ознака: celebration")
        if request.terrace and not _flag(self._terrace.is_open(), "Доступність тераси"):
            return Result("Тераса недоступна")
        available = _count(self._seats.free_seats(), "Вільні місця")
        if available < request.guests:
            return Result("Недостатньо місць")
        base = request.guests * 10_000 if request.guests >= 6 else 0
        if request.terrace and request.celebration:
            options = 13_000
        elif request.terrace:
            options = 5_000
        elif request.celebration:
            options = 8_000
        else:
            options = 0
        urgent = 3_000 if request.hours_before <= 24 else 0
        result = Result("Підтверджено", request.guests,
                        "Тераса" if request.terrace else "Зал", base + options + urgent)
        self._reservations.save(request, result)
        return result
