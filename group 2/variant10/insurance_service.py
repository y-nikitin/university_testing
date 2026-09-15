from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    age: int
    insured_amount: int
    smoker: bool
    hazardous_activity: bool


@dataclass(frozen=True)
class Result:
    status: str
    insured_amount: int = 0
    base_premium: int = 0
    age_surcharge: int = 0
    risk_surcharge: int = 0
    discount_percent: int = 0
    total: int = 0


class UnderwritingPolicy(Protocol):
    def is_allowed(self) -> bool:
        ...


class ClaimHistory(Protocol):
    def has_no_claims_discount(self) -> bool:
        ...


class QuoteRepository(Protocol):
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


class InsuranceService:
    def __init__(self, policy: UnderwritingPolicy, history: ClaimHistory, quotes: QuoteRepository) -> None:
        if policy is None or history is None or quotes is None:
            raise ValueError("Усі залежності мають бути задані")
        self._policy = policy
        self._history = history
        self._quotes = quotes

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.age, 18, 74, "Вік")
        _range(request.insured_amount, 100_000, 10_000_000, "Страхова сума")
        if type(request.smoker) is not bool or type(request.hazardous_activity) is not bool:
            raise ValueError("Ознаки мають бути логічними значеннями")
        if not _flag(self._policy.is_allowed(), "Доступність страхування"):
            return Result("Страхування недоступне")
        base = request.insured_amount * 2 // 100
        age_fee = 5_000 if request.age < 25 else 8_000 if request.age > 65 else 0
        if request.smoker and request.hazardous_activity:
            risk = 10_000
        elif request.smoker:
            risk = 4_000
        elif request.hazardous_activity:
            risk = 6_000
        else:
            risk = 0
        subtotal = base + age_fee + risk
        discount = 10 if _flag(self._history.has_no_claims_discount(), "Пільга за історією") else 0
        total = max(3_000, subtotal - subtotal * discount // 100)
        result = Result("Пропозицію сформовано", request.insured_amount, base,
                        age_fee, risk, discount, total)
        self._quotes.save(request, result)
        return result
