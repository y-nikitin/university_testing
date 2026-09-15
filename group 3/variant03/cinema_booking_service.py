from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    tickets: int
    age: int
    evening: bool
    student_claimed: bool


@dataclass(frozen=True)
class Result:
    status: str
    tickets: int = 0
    total: int = 0


class SeatAvailability(Protocol):
    def free_seats(self) -> int:
        ...


class StudentRegistry(Protocol):
    def is_eligible(self) -> bool:
        ...


class TicketRepository(Protocol):
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


class CinemaBookingService:
    def __init__(self, seats: SeatAvailability, students: StudentRegistry, tickets: TicketRepository) -> None:
        if seats is None or students is None or tickets is None:
            raise ValueError("Усі залежності мають бути задані")
        self._seats = seats
        self._students = students
        self._tickets = tickets

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.tickets, 1, 7, "Кількість квитків")
        _range(request.age, 0, 120, "Вік")
        if type(request.evening) is not bool or type(request.student_claimed) is not bool:
            raise ValueError("Ознаки мають бути логічними значеннями")
        if request.age < 11 and request.evening:
            return Result("Вікове обмеження")
        available = _count(self._seats.free_seats(), "Вільні місця")
        if available < request.tickets:
            return Result("Недостатньо місць")
        if request.age < 12:
            discount = 50
        elif request.student_claimed and _flag(self._students.is_eligible(), "Студентська пільга"):
            discount = 20
        else:
            discount = 0
        base = request.tickets * (15_000 if request.evening else 10_000)
        total = base - base * discount // 100
        if request.tickets > 5:
            total -= 2_000
        result = Result("Заброньовано", request.tickets, total)
        self._tickets.save(request, result)
        return result
