from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    age: int
    days: int
    premium: bool
    insured: bool


@dataclass(frozen=True)
class Result:
    status: str
    rental: int = 0
    age_surcharge: int = 0
    insurance: int = 0
    deposit: int = 0
    total: int = 0


class FleetAvailability(Protocol):
    def is_available(self) -> bool:
        ...


class DriverHistory(Protocol):
    def has_incident(self) -> bool:
        ...


class RentalRepository(Protocol):
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


class CarRentalService:
    def __init__(self, fleet: FleetAvailability, history: DriverHistory, rentals: RentalRepository) -> None:
        if fleet is None or history is None or rentals is None:
            raise ValueError("Усі залежності мають бути задані")
        self._fleet = fleet
        self._history = history
        self._rentals = rentals

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.age, 21, 74, "Вік водія")
        _range(request.days, 1, 30, "Строк оренди")
        if type(request.premium) is not bool or type(request.insured) is not bool:
            raise ValueError("Ознаки мають бути логічними значеннями")
        if not _flag(self._fleet.is_available(), "Доступність автомобіля"):
            return Result("Немає автомобіля")
        young = request.age < 25
        if (young and request.premium) and _flag(self._history.has_incident(), "Історія водія"):
            return Result("Відмова за історією")
        base = request.days * (90_000 if request.premium else 50_000)
        rental = base - base * 10 // 100 if request.days > 7 else base
        age_surcharge = request.days * 10_000 if young else 0
        insurance = request.days * 8_000 if request.insured else 0
        deposit = 100_000 if request.insured else 200_000
        result = Result("Оформлено", rental, age_surcharge, insurance, deposit,
                        rental + age_surcharge + insurance + deposit)
        self._rentals.save(request, result)
        return result
