from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    participants: int
    days_before: int
    student_claimed: bool
    allow_waitlist: bool


@dataclass(frozen=True)
class Result:
    status: str
    participants: int = 0
    total: int = 0


class EventCapacity(Protocol):
    def free_seats(self) -> int:
        ...

class StudentRegistry(Protocol):
    def is_eligible(self) -> bool:
        ...

class RegistrationRepository(Protocol):
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


class EventRegistrationService:
    def __init__(self, capacity: EventCapacity, students: StudentRegistry, registrations: RegistrationRepository) -> None:
        if capacity is None or students is None or registrations is None:
            raise ValueError("Усі залежності мають бути задані")
        self._capacity = capacity
        self._students = students
        self._registrations = registrations

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.participants, 1, 6, "Кількість учасників")
        _range(request.days_before, 0, 179, "Дні до події")
        if type(request.student_claimed) is not bool:
            raise ValueError("Недопустима логічна ознака: student_claimed")
        if type(request.allow_waitlist) is not bool:
            raise ValueError("Недопустима логічна ознака: allow_waitlist")
        if request.days_before == 0:
            return Result("Реєстрацію закрито")
        seats = _count(self._capacity.free_seats(), "Вільні місця")
        if seats < request.participants:
            if not request.allow_waitlist:
                return Result("Немає місць")
            result = Result("У списку очікування", request.participants, 0)
            self._registrations.save(request, result)
            return result
        student = request.student_claimed and _flag(self._students.is_eligible(), "Студентська пільга")
        base = request.participants * 30_000
        discount = 20 if student else 0
        total = base - base * discount // 100
        if request.days_before > 30:
            total -= 2_000
        result = Result("Зареєстровано", request.participants, total)
        self._registrations.save(request, result)
        return result
