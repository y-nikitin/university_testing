from dataclasses import dataclass
from enum import StrEnum

class RentalStatus(StrEnum):
    ACTIVE = "Active"
    RETURNED = "Returned"

@dataclass(frozen=True)
class Equipment:
    id: int
    code: str
    daily_rate: int
    stock: int

@dataclass(frozen=True)
class RentalItem:
    id: int
    equipment_id: int
    quantity: int
    daily_rate: int

@dataclass(frozen=True)
class RentalOrder:
    id: int
    reference: str
    days: int
    premium: bool
    total: int
    late_fee: int
    status: RentalStatus
    item: RentalItem

@dataclass(frozen=True)
class RentalRequest:
    reference: str
    equipment_id: int
    quantity: int
    days: int
    premium: bool = False
