from dataclasses import replace
from .models import ShipmentRequest, Shipment, ShipmentStatus
from .ports import CustomerRepository, ParcelRepository, ShipmentRepository, ShippingPolicy

class ShipmentRejectedError(Exception):
    pass

def integer(value, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ValueError("Ціле число поза допустимими межами")

def boolean(value):
    if type(value) is not bool:
        raise ValueError("Потрібна логічна ознака bool")

class ShippingService:
    def __init__(self, customers: CustomerRepository, parcels: ParcelRepository, shipments: ShipmentRepository, policy: ShippingPolicy):
        if any(value is None for value in (customers, parcels, shipments, policy)):
            raise ValueError("Усі залежності обов'язкові")
        self.customers, self.parcels, self.shipments, self.policy = customers, parcels, shipments, policy

    def calculate_base_fee(self, weight_grams, distance_km):
        integer(weight_grams, 1, 30000)
        integer(distance_km, 1, 5000)
        distance_fee = distance_km * 10
        return 5000 + ((weight_grams + 999) // 1000) * 1000 + distance_fee

    def is_urgent_allowed(self, weight_grams, distance_km, urgent):
        self.calculate_base_fee(weight_grams, distance_km)
        boolean(urgent)
        return not urgent or (weight_grams <= 10000 and distance_km <= 500)

    def create_shipment(self, request):
        if not isinstance(request, ShipmentRequest):
            raise ValueError("Некоректна заявка")
        if not isinstance(request.reference, str) or not request.reference.strip() or len(request.reference) > 40:
            raise ValueError("Код відправлення має містити 1–40 символів")
        integer(request.customer_id, 1, 2**63 - 1)
        integer(request.parcel_id, 1, 2**63 - 1)
        integer(request.distance_km, 1, 5000)
        for value in (request.urgent, request.pickup, request.coupon_valid):
            boolean(value)
        customer = self.customers.find_by_id(request.customer_id)
        if customer is None:
            raise ValueError("Клієнта не знайдено")
        parcel = self.parcels.find_by_id(request.parcel_id)
        if parcel is None:
            raise ValueError("Посилку не знайдено")
        integer(parcel.declared_value, 0, 100000000)
        boolean(customer.regular)
        base_fee = self.calculate_base_fee(parcel.weight_grams, request.distance_km)
        if not self.is_urgent_allowed(parcel.weight_grams, request.distance_km, request.urgent):
            raise ShipmentRejectedError("Умови термінової доставки не виконані")
        allowed = self.policy.is_allowed(customer, parcel, request.distance_km)
        boolean(allowed)
        if not allowed:
            raise ShipmentRejectedError("Доставку не дозволено")
        adjustment = 0
        return self.shipments.create(Shipment(0, request.reference, customer.id, parcel.id, request.distance_km,
            request.urgent, request.pickup, customer.regular, request.coupon_valid, parcel.weight_grams, parcel.declared_value,
            base_fee, adjustment, base_fee + adjustment, ShipmentStatus.REGISTERED))

    def dispatch(self, shipment_id):
        return self._change_status(shipment_id, ShipmentStatus.REGISTERED, ShipmentStatus.DISPATCHED)

    def deliver(self, shipment_id):
        return self._change_status(shipment_id, ShipmentStatus.DISPATCHED, ShipmentStatus.DELIVERED)

    def cancel(self, shipment_id):
        return self._change_status(shipment_id, ShipmentStatus.REGISTERED, ShipmentStatus.CANCELLED)

    def _change_status(self, shipment_id, expected, next_status):
        integer(shipment_id, 1, 2**63 - 1)
        shipment = self.shipments.find_by_id(shipment_id)
        if shipment is None:
            raise ValueError("Відправлення не знайдено")
        if shipment.status != expected:
            raise ShipmentRejectedError("Перехід статусу заборонений")
        self.shipments.update_status(shipment_id, expected, next_status)
        return replace(shipment, status=next_status)
