from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    age: int
    minutes: int
    referral: bool
    remote: bool


@dataclass(frozen=True)
class Result:
    status: str
    minutes: int = 0
    remote: bool = False
    total: int = 0


class GuardianConsent(Protocol):
    def is_granted(self) -> bool:
        ...


class ScheduleAvailability(Protocol):
    def free_minutes(self) -> int:
        ...


class AppointmentRepository(Protocol):
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


class AppointmentService:
    def __init__(self, consent: GuardianConsent, schedule: ScheduleAvailability, appointments: AppointmentRepository) -> None:
        if consent is None or schedule is None or appointments is None:
            raise ValueError("Усі залежності мають бути задані")
        self._consent = consent
        self._schedule = schedule
        self._appointments = appointments

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.age, 0, 120, "Вік")
        _range(request.minutes, 15, 60, "Тривалість")
        if request.minutes % 15 != 0:
            raise ValueError("Тривалість має становити 15, 30, 45 або 60 хвилин")
        if type(request.referral) is not bool or type(request.remote) is not bool:
            raise ValueError("Ознаки мають бути логічними значеннями")
        if request.age <= 18 and not _flag(self._consent.is_granted(), "Згода представника"):
            return Result("Потрібна згода представника")
        if request.remote and request.minutes > 30:
            return Result("Недоступний формат")
        available = _count(self._schedule.free_minutes(), "Доступний проміжок")
        if available < request.minutes:
            return Result("Недостатній проміжок")
        if request.referral and request.remote:
            discount = 20
        elif request.referral:
            discount = 15
        elif request.remote:
            discount = 10
        else:
            discount = 0
        base = request.minutes // 15 * 10_000
        total = base - base * discount // 100
        if request.age > 65:
            total = max(0, total - 2_000)
        result = Result("Записано", request.minutes, request.remote, total)
        self._appointments.save(request, result)
        return result
