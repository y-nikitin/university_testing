from dataclasses import dataclass
from enum import StrEnum

class ShipmentStatus(StrEnum):
    REGISTERED = "Registered"
    DISPATCHED = "Dispatched"
    DELIVERED = "Delivered"
    CANCELLED = "Cancelled"

@dataclass(frozen=True)
class Customer:
    id: int
    code: str
    regular: bool

@dataclass(frozen=True)
class Parcel:
    id: int
    code: str
    weight_grams: int
    declared_value: int

@dataclass(frozen=True)
class Shipment:
    id: int
    reference: str
    customer_id: int
    parcel_id: int
    distance_km: int
    urgent: bool
    pickup: bool
    regular: bool
    coupon_valid: bool
    weight_grams: int
    declared_value: int
    base_fee: int
    adjustment: int
    total: int
    status: ShipmentStatus

@dataclass(frozen=True)
class ShipmentRequest:
    reference: str
    customer_id: int
    parcel_id: int
    distance_km: int
    urgent: bool = False
    pickup: bool = False
    coupon_valid: bool = False
