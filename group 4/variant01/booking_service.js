"use strict";

class Request {
    constructor(nights, guests, loyaltyClaimed, breakfast) {
        this.nights = nights;
        this.guests = guests;
        this.loyaltyClaimed = loyaltyClaimed;
        this.breakfast = breakfast;
        Object.freeze(this);
    }
}

class Result {
    constructor(status, nights = 0, guests = 0, discountPercent = 0, accommodation = 0, breakfastCost = 0, total = 0) {
        this.status = status;
        this.nights = nights;
        this.guests = guests;
        this.discountPercent = discountPercent;
        this.accommodation = accommodation;
        this.breakfastCost = breakfastCost;
        this.total = total;
        Object.freeze(this);
    }
}

class RoomAvailability {
    isAvailable() {
        throw new Error("Потрібно надати залежність: RoomAvailability");
    }
}

class LoyaltyProvider {
    isMember() {
        throw new Error("Потрібно надати залежність: LoyaltyProvider");
    }
}

class BookingRepository {
    save(request, result) {
        throw new Error("Потрібно надати залежність: BookingRepository");
    }
}

function checkRange(value, minimum, maximum, field) {
    if (!Number.isSafeInteger(value) || value < minimum || value > maximum) {
        throw new RangeError("Недопустиме значення: " + field);
    }
}

function checkFlags(...values) {
    if (values.some(value => typeof value !== "boolean")) {
        throw new TypeError("Ознаки мають бути логічними значеннями");
    }
}

function readFlag(value, field) {
    if (typeof value !== "boolean") {
        throw new Error("Некоректні відомості: " + field);
    }
    return value;
}

function readCount(value, field) {
    if (!Number.isSafeInteger(value) || value < 0) {
        throw new Error("Некоректні відомості: " + field);
    }
    return value;
}

class BookingService {
    constructor(rooms, loyalty, bookings) {
        if (rooms == null || loyalty == null || bookings == null) {
            throw new TypeError("Усі залежності мають бути задані");
        }
        this.rooms = rooms;
        this.loyalty = loyalty;
        this.bookings = bookings;
    }

    process(request) {
        if (!(request instanceof Request)) {
            throw new TypeError("Заявку не задано або її формат некоректний");
        }
        checkRange(request.nights, 1, 29, "Кількість ночей");
        checkRange(request.guests, 1, 4, "Кількість гостей");
        checkFlags(request.loyaltyClaimed, request.breakfast);
        if (!readFlag(this.rooms.isAvailable(), "Доступність номера")) {
            return new Result("Немає номера");
        }
        const member = request.loyaltyClaimed && readFlag(this.loyalty.isMember(), "Участь у програмі лояльності");
        const longStay = request.nights > 7;
        let discount;
        if (member && longStay) {
            discount = 15;
        } else if (member) {
            discount = 10;
        } else if (longStay) {
            discount = 5;
        } else {
            discount = 0;
        }
        const base = request.nights * 120_000;
        const accommodation = base - Math.floor(base * discount / 100);
        const breakfast = request.breakfast ? request.nights * Math.min(request.guests, 3) * 15_000 : 0;
        const result = new Result("Підтверджено", request.nights, request.guests, discount,
            accommodation, breakfast, accommodation + breakfast);
        this.bookings.save(request, result);
        return result;
    }
}

module.exports = { Request, Result, RoomAvailability, LoyaltyProvider, BookingRepository, BookingService };
