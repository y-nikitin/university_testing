from dataclasses import dataclass
from enum import StrEnum


class OrderStatus(StrEnum):
    CONFIRMED = "CONFIRMED"
    RETURNED = "RETURNED"


@dataclass(frozen=True)
class Product:
    id: int
    sku: str
    unit_price: int
    stock: int
    on_sale: bool


@dataclass(frozen=True)
class OrderItem:
    id: int
    product_id: int
    quantity: int
    unit_price: int
    on_sale: bool


@dataclass(frozen=True)
class Order:
    id: int
    reference: str
    premium: bool
    coupon_valid: bool
    total: int
    status: OrderStatus
    item: OrderItem


@dataclass(frozen=True)
class OrderRequest:
    reference: str
    product_id: int
    quantity: int
    premium: bool
    coupon_claimed: bool
