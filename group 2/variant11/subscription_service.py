from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    users: int
    months: int
    nonprofit_claimed: bool
    auto_renew: bool


@dataclass(frozen=True)
class Result:
    status: str
    total: int = 0
    restoration_fee: int = 0
    subscription_state: str | None = None


class SubscriptionState(Protocol):
    def current_status(self) -> str:
        ...


class NonprofitRegistry(Protocol):
    def is_eligible(self) -> bool:
        ...


class BillingRepository(Protocol):
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


class SubscriptionService:
    def __init__(self, subscriptions: SubscriptionState, nonprofits: NonprofitRegistry, billing: BillingRepository) -> None:
        if subscriptions is None or nonprofits is None or billing is None:
            raise ValueError("Усі залежності мають бути задані")
        self._subscriptions = subscriptions
        self._nonprofits = nonprofits
        self._billing = billing

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.users, 1, 49, "Кількість користувачів")
        _range(request.months, 1, 12, "Строк продовження")
        if type(request.nonprofit_claimed) is not bool or type(request.auto_renew) is not bool:
            raise ValueError("Ознаки пільг мають бути логічними значеннями")
        state = self._subscriptions.current_status()
        if state not in ("Активна", "Прострочена", "Заблокована"):
            raise RuntimeError("Невідомий стан підписки")
        if state == "Заблокована":
            return Result("Підписку заблоковано", 0, 0, state)
        nonprofit = request.nonprofit_claimed and _flag(self._nonprofits.is_eligible(), "Неприбутковість")
        if nonprofit and request.auto_renew:
            discount = 20
        elif nonprofit:
            discount = 20
        elif request.auto_renew:
            discount = 5
        else:
            discount = 0
        base = request.users * request.months * 20_000
        total = base - base * discount // 100
        if request.months >= 11:
            total = max(0, total - 10_000)
        restoration_fee = 5_000 if state == "Прострочена" else 0
        result = Result("Продовжено", total + restoration_fee, restoration_fee, "Активна")
        self._billing.save(request, result)
        return result
