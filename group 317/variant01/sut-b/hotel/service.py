from dataclasses import replace
from datetime import date
from .models import Booking, BookingItem, BookingRequest, BookingStatus
from .ports import RoomRepository, BookingRepository, BookingPolicy

class ResourceNotFoundError(ValueError):
    pass


class BookingRejectedError(Exception):
    pass

def integer(value, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ValueError("Ціле число поза допустимими межами")

class BookingService:
    def __init__(self, rooms: RoomRepository, bookings: BookingRepository, policy: BookingPolicy):
        if rooms is None or bookings is None or policy is None:
            raise ValueError("Усі залежності обов'язкові")
        self.rooms, self.bookings, self.policy = rooms, bookings, policy

    def calculate_total(self, nightly_rate, nights):
        integer(nightly_rate, 100, 1000000)
        integer(nights, 1, 30)
        subtotal = nightly_rate * nights
        discount = 15 if nights >= 14 else 10 if nights >= 7 else 0
        return subtotal - subtotal * discount // 100

    def calculate_prepayment(self, total, regular=False):
        integer(total, 0, 30000000)
        if type(regular) is not bool:
            raise ValueError("Ознака постійного клієнта має бути bool")
        return total

    def calculate_refund(self, prepaid, days_before):
        integer(prepaid, 0, 30000000)
        integer(days_before, 0, 36500)
        return prepaid

    def book(self, request):
        if not isinstance(request, BookingRequest):
            raise ValueError("Некоректна заявка")
        if not isinstance(request.reference, str) or not request.reference.strip() or len(request.reference) > 40:
            raise ValueError("Код бронювання має містити 1–40 символів")
        integer(request.room_id, 1, 2**63 - 1)
        integer(request.guests, 1, 6)
        if type(request.regular) is not bool or type(request.check_in) is not date or type(request.check_out) is not date:
            raise ValueError("Некоректні дати або ознака клієнта")
        nights = (request.check_out - request.check_in).days
        integer(nights, 1, 30)
        room = self.rooms.find_by_id(request.room_id)
        if room is None:
            raise ResourceNotFoundError("Номер не знайдено")
        if request.guests > room.capacity:
            raise BookingRejectedError("Перевищено місткість номера")
        if not self.bookings.is_available(room.id, request.check_in, request.check_out):
            raise BookingRejectedError("Номер зайнятий")
        allowed = self.policy.is_allowed(room, request.guests, nights)
        if type(allowed) is not bool:
            raise ValueError("Некоректна відповідь зовнішньої перевірки")
        if not allowed:
            raise BookingRejectedError("Бронювання не дозволено")
        total = self.calculate_total(room.nightly_rate, nights)
        prepaid = self.calculate_prepayment(total, request.regular)
        return self.bookings.create(Booking(0, request.reference, request.check_in, request.check_out,
            request.guests, request.regular, total, prepaid, 0, BookingStatus.CONFIRMED, BookingItem(0, room.id, room.nightly_rate)))

    def cancel_booking(self, booking_id, days_before):
        integer(booking_id, 1, 2**63 - 1)
        integer(days_before, 0, 36500)
        booking = self.bookings.find_by_id(booking_id)
        if booking is None:
            raise ResourceNotFoundError("Бронювання не знайдено")
        if booking.status == BookingStatus.CANCELLED:
            raise BookingRejectedError("Бронювання вже скасовано")
        refund = self.calculate_refund(booking.prepaid, days_before)
        self.bookings.cancel(booking_id, refund)
        return replace(booking, status=BookingStatus.CANCELLED, refund=refund)
