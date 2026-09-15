from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    months: int
    visits: int
    off_peak: bool
    corporate_claimed: bool


@dataclass(frozen=True)
class Result:
    status: str
    months: int = 0
    visits: int = 0
    discount_percent: int = 0
    entry_fee: int = 0
    total: int = 0


class ClubCapacity(Protocol):
    def accepts_members(self) -> bool:
        ...

class CorporateRegistry(Protocol):
    def is_eligible(self) -> bool:
        ...

class MembershipRepository(Protocol):
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


class MembershipService:
    def __init__(self, capacity: ClubCapacity, corporate: CorporateRegistry, memberships: MembershipRepository) -> None:
        if capacity is None or corporate is None or memberships is None:
            raise ValueError("Усі залежності мають бути задані")
        self._capacity = capacity
        self._corporate = corporate
        self._memberships = memberships

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.months, 1, 12, "Строк абонемента")
        _range(request.visits, 1, 29, "Кількість відвідувань")
        if type(request.off_peak) is not bool:
            raise ValueError("Недопустима логічна ознака: off_peak")
        if type(request.corporate_claimed) is not bool:
            raise ValueError("Недопустима логічна ознака: corporate_claimed")
        if not _flag(self._capacity.accepts_members(), "Прийом клієнтів"):
            return Result("Немає доступних абонементів")
        corporate = request.corporate_claimed and _flag(self._corporate.is_eligible(), "Корпоративна пільга")
        if request.off_peak and corporate:
            discount = 20
        elif corporate:
            discount = 15
        elif request.off_peak:
            discount = 10
        else:
            discount = 0
        monthly = 200_000 + max(0, request.visits - 12) * 5_000
        base = request.months * monthly
        entry_fee = 0 if request.months >= 11 else 10_000
        total = base - base * discount // 100 + entry_fee
        result = Result("Активовано", request.months, request.visits, discount, entry_fee, total)
        self._memberships.save(request, result)
        return result
