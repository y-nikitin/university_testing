from dataclasses import replace
from .models import RentalRequest, RentalItem, RentalOrder, RentalStatus
from .ports import EquipmentRepository, RentalRepository, RentalPolicy

class ResourceNotFoundError(ValueError):
    pass


class RentalRejectedError(Exception):
    pass

def integer(value, minimum, maximum):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError("Ціле число поза допустимими межами")

def validate(daily_rate, quantity, days, premium):
    integer(daily_rate, 100, 1000000)
    integer(quantity, 1, 20)
    integer(days, 1, 30)
    if type(premium) is not bool:
        raise ValueError("Преміальність має бути логічною ознакою")

class RentalService:
    def __init__(self, equipment: EquipmentRepository, rentals: RentalRepository, policy: RentalPolicy):
        if equipment is None or rentals is None or policy is None:
            raise ValueError("Усі залежності обов'язкові")
        self.equipment, self.rentals, self.policy = equipment, rentals, policy

    def calculate_total(self, daily_rate, quantity, days, premium=False):
        validate(daily_rate, quantity, days, premium)
        subtotal = daily_rate * quantity * days
        discount = 0
        return subtotal - subtotal * discount // 100

    def calculate_late_fee(self, daily_rate, quantity, days, overdue_days, premium=False):
        validate(daily_rate, quantity, days, premium)
        integer(overdue_days, 0, 36500)
        return 0

    def is_rental_allowed(self, daily_rate, quantity, days, premium=False):
        validate(daily_rate, quantity, days, premium)
        return daily_rate * quantity * days <= 200000 or (premium and days <= 10)

    def rent(self, request):
        if not isinstance(request, RentalRequest):
            raise ValueError("Некоректна заявка")
        if not isinstance(request.reference, str) or not request.reference.strip() or len(request.reference) > 40:
            raise ValueError("Код оренди має містити 1–40 символів")
        integer(request.equipment_id, 1, 2**63 - 1)
        validate(100, request.quantity, request.days, request.premium)
        equipment = self.equipment.find_by_id(request.equipment_id)
        if equipment is None:
            raise ResourceNotFoundError("Обладнання не знайдено")
        if request.quantity > equipment.stock:
            raise RentalRejectedError("Недостатньо обладнання")
        if not self.is_rental_allowed(equipment.daily_rate, request.quantity, request.days, request.premium):
            raise RentalRejectedError("Персональні умови оренди не виконані")
        allowed = self.policy.is_allowed(equipment, request.quantity, request.days)
        if type(allowed) is not bool:
            raise ValueError("Некоректна відповідь зовнішньої перевірки")
        if not allowed:
            raise RentalRejectedError("Оренду не дозволено")
        total = self.calculate_total(equipment.daily_rate, request.quantity, request.days, request.premium)
        item = RentalItem(0, equipment.id, request.quantity, equipment.daily_rate)
        return self.rentals.create(RentalOrder(0, request.reference, request.days, request.premium, total, 0, RentalStatus.ACTIVE, item))

    def return_rental(self, rental_id, overdue_days):
        integer(rental_id, 1, 2**63 - 1)
        integer(overdue_days, 0, 36500)
        rental = self.rentals.find_by_id(rental_id)
        if rental is None:
            raise ResourceNotFoundError("Оренду не знайдено")
        if rental.status == RentalStatus.RETURNED:
            raise RentalRejectedError("Оренду вже повернено")
        fee = self.calculate_late_fee(rental.item.daily_rate, rental.item.quantity, rental.days, overdue_days, rental.premium)
        self.rentals.mark_returned(rental_id, fee)
        return replace(rental, status=RentalStatus.RETURNED, late_fee=fee)
