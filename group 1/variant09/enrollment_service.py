from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    course_credits: int
    current_credits: int
    scholarship: bool
    early: bool


@dataclass(frozen=True)
class Result:
    status: str
    credits: int = 0
    discount_percent: int = 0
    total: int = 0
    remaining_seats: int = 0


class PrerequisitePolicy(Protocol):
    def is_satisfied(self) -> bool:
        ...

class CourseCapacity(Protocol):
    def free_seats(self) -> int:
        ...

class EnrollmentRepository(Protocol):
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


class EnrollmentService:
    def __init__(self, prerequisites: PrerequisitePolicy, capacity: CourseCapacity, enrollments: EnrollmentRepository) -> None:
        if prerequisites is None or capacity is None or enrollments is None:
            raise ValueError("Усі залежності мають бути задані")
        self._prerequisites = prerequisites
        self._capacity = capacity
        self._enrollments = enrollments

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.course_credits, 1, 11, "Кредити курсу")
        _range(request.current_credits, 0, 30, "Поточне навантаження")
        if type(request.scholarship) is not bool:
            raise ValueError("Недопустима логічна ознака: scholarship")
        if type(request.early) is not bool:
            raise ValueError("Недопустима логічна ознака: early")
        credits = request.current_credits + request.course_credits
        if credits >= 30:
            return Result("Перевищено навантаження")
        if not _flag(self._prerequisites.is_satisfied(), "Передумови курсу"):
            return Result("Не виконано передумови")
        seats = _count(self._capacity.free_seats(), "Вільні місця")
        if seats == 0:
            return Result("Немає місць")
        if request.scholarship and request.early:
            discount = 25
        elif request.scholarship:
            discount = 20
        elif request.early:
            discount = 10
        else:
            discount = 0
        base = request.course_credits * 50_000
        result = Result("Зараховано", credits, discount, base - base * discount // 100, seats - 1)
        self._enrollments.save(request, result)
        return result
