from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True)
class Request:
    quantity: int
    reserved: int
    urgent: bool
    allow_partial: bool


@dataclass(frozen=True)
class Result:
    status: str
    requested: int = 0
    issued: int = 0
    stock: int = 0
    reserved: int = 0
    fee: int = 0


class ReleasePolicy(Protocol):
    def is_allowed(self) -> bool:
        ...

class StockProvider(Protocol):
    def physical_stock(self) -> int:
        ...

class WarehouseRepository(Protocol):
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


class WarehouseService:
    def __init__(self, policy: ReleasePolicy, stock: StockProvider, orders: WarehouseRepository) -> None:
        if policy is None or stock is None or orders is None:
            raise ValueError("Усі залежності мають бути задані")
        self._policy = policy
        self._stock = stock
        self._orders = orders

    def process(self, request: Request) -> Result:
        if not isinstance(request, Request):
            raise ValueError("Заявку не задано або її формат некоректний")
        _range(request.quantity, 1, 99, "Запитана кількість")
        _range(request.reserved, 0, 100, "Попередні резерви")
        if type(request.urgent) is not bool:
            raise ValueError("Недопустима логічна ознака: urgent")
        if type(request.allow_partial) is not bool:
            raise ValueError("Недопустима логічна ознака: allow_partial")
        if not _flag(self._policy.is_allowed(), "Дозвіл на видачу"):
            return Result("Видачу заблоковано")
        stock = _count(self._stock.physical_stock(), "Фізичний залишок")
        if request.reserved > stock:
            raise RuntimeError("Резерви перевищують фізичний залишок")
        available = stock - request.reserved
        if available == 0:
            return Result("Немає доступного товару")
        if available < request.quantity and not request.allow_partial:
            return Result("Недостатньо товару")
        issued = min(available, request.quantity)
        partial = issued < request.quantity
        if request.urgent and partial:
            surcharge = 5_000
        elif request.urgent:
            surcharge = 5_000
        elif partial:
            surcharge = 1_000
        else:
            surcharge = 0
        remaining = stock - issued - (request.reserved if partial else 0)
        result = Result("Виконано частково" if partial else "Виконано повністю",
                        request.quantity, issued, remaining, request.reserved, issued * 500 + surcharge)
        self._orders.save(request, result)
        return result
