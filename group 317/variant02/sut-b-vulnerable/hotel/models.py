from dataclasses import dataclass
from datetime import date
from enum import StrEnum

class BookingStatus(StrEnum):
    CONFIRMED = "CONFIRMED"
    CANCELLED = "CANCELLED"

@dataclass(frozen=True)
class Room:
    id: int
    code: str
    nightly_rate: int
    capacity: int

@dataclass(frozen=True)
class BookingItem:
    id: int
    room_id: int
    nightly_rate: int

@dataclass(frozen=True)
class Booking:
    id: int
    reference: str
    check_in: date
    check_out: date
    guests: int
    regular: bool
    total: int
    prepaid: int
    refund: int
    status: BookingStatus
    item: BookingItem

@dataclass(frozen=True)
class BookingRequest:
    reference: str
    room_id: int
    check_in: date
    check_out: date
    guests: int
    regular: bool = False
