"use strict";

class Request {
    constructor(travelers, daysBefore, premium, flexible) {
        this.travelers = travelers;
        this.daysBefore = daysBefore;
        this.premium = premium;
        this.flexible = flexible;
        Object.freeze(this);
    }
}

class Result {
    constructor(status, travelers = 0, discountPercent = 0, options = 0, lateFee = 0, total = 0) {
        this.status = status;
        this.travelers = travelers;
        this.discountPercent = discountPercent;
        this.options = options;
        this.lateFee = lateFee;
        this.total = total;
        Object.freeze(this);
    }
}

class RoutePolicy {
    isOpen() {
        throw new Error("Потрібно надати залежність: RoutePolicy");
    }
}

class TravelCapacity {
    freeSeats() {
        throw new Error("Потрібно надати залежність: TravelCapacity");
    }
}

class TravelRepository {
    save(request, result) {
        throw new Error("Потрібно надати залежність: TravelRepository");
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

class TravelBookingService {
    constructor(route, capacity, bookings) {
        if (route == null || capacity == null || bookings == null) {
            throw new TypeError("Усі залежності мають бути задані");
        }
        this.route = route;
        this.capacity = capacity;
        this.bookings = bookings;
    }

    process(request) {
        if (!(request instanceof Request)) {
            throw new TypeError("Заявку не задано або її формат некоректний");
        }
        checkRange(request.travelers, 1, 6, "Кількість туристів");
        checkRange(request.daysBefore, 1, 179, "Дні до виїзду");
        checkFlags(request.premium, request.flexible);
        if (!readFlag(this.route.isOpen(), "Відкритість напрямку")) {
            return new Result("Напрямок закритий");
        }
        const seats = readCount(this.capacity.freeSeats(), "Вільні місця");
        if (seats < request.travelers) {
            return new Result("Недостатньо місць");
        }
        let optionRate;
        if (request.premium && request.flexible) {
            optionRate = 45_000;
        } else if (request.premium) {
            optionRate = 25_000;
        } else if (request.flexible) {
            optionRate = 20_000;
        } else {
            optionRate = 0;
        }
        const base = request.travelers * 100_000;
        const discount = request.daysBefore > 60 ? 10 : 0;
        const options = request.travelers * optionRate;
        const lateFee = request.daysBefore < 7 ? request.travelers * 15_000 : 0;
        const total = base - Math.floor(base * discount / 100) + options + lateFee;
        const result = new Result("Заброньовано", request.travelers, discount, options, lateFee, total);
        this.bookings.save(request, result);
        return result;
    }
}

module.exports = { Request, Result, RoutePolicy, TravelCapacity, TravelRepository, TravelBookingService };
