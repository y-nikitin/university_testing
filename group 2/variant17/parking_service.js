"use strict";

class Request {
    constructor(minutes, entryHour, zone, electric, subscriptionClaimed) {
        this.minutes = minutes;
        this.entryHour = entryHour;
        this.zone = zone;
        this.electric = electric;
        this.subscriptionClaimed = subscriptionClaimed;
        Object.freeze(this);
    }
}

class Result {
    constructor(status, billableHours = 0, total = 0) {
        this.status = status;
        this.billableHours = billableHours;
        this.total = total;
        Object.freeze(this);
    }
}

class ParkingAvailability {
    isAvailable() {
        throw new Error("Потрібно надати залежність: ParkingAvailability");
    }
}

class SubscriptionRegistry {
    isValid() {
        throw new Error("Потрібно надати залежність: SubscriptionRegistry");
    }
}

class ParkingRepository {
    save(request, result) {
        throw new Error("Потрібно надати залежність: ParkingRepository");
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

class ParkingService {
    constructor(parking, subscriptions, receipts) {
        if (parking == null || subscriptions == null || receipts == null) {
            throw new TypeError("Усі залежності мають бути задані");
        }
        this.parking = parking;
        this.subscriptions = subscriptions;
        this.receipts = receipts;
    }

    process(request) {
        if (!(request instanceof Request)) {
            throw new TypeError("Заявку не задано або її формат некоректний");
        }
        checkRange(request.minutes, 1, 1_439, "Тривалість");
        checkRange(request.entryHour, 0, 23, "Година в’їзду");
        checkFlags(request.electric, request.subscriptionClaimed);
        if (!["Центральна", "Зовнішня"].includes(request.zone)) {
            throw new RangeError("Невідома зона");
        }
        if (!readFlag(this.parking.isAvailable(), "Доступність оформлення")) {
            return new Result("Оформлення недоступне");
        }
        if (request.minutes <= 15) {
            const result = new Result("Розраховано", 0, 0);
            this.receipts.save(request, result);
            return result;
        }
        const hours = Math.floor((request.minutes - 15 + 59) / 60);
        const central = request.zone === "Центральна";
        const base = Math.min(hours * (central ? 5_000 : 3_000), central ? 30_000 : 20_000);
        const subscriber = request.subscriptionClaimed && readFlag(this.subscriptions.isValid(), "Чинність абонемента");
        let discount;
        if (request.electric && subscriber) {
            discount = 35;
        } else if (subscriber) {
            discount = 30;
        } else if (request.electric) {
            discount = 10;
        } else {
            discount = 0;
        }
        let total = base - Math.floor(base * discount / 100);
        if (request.entryHour >= 22 || request.entryHour < 5) {
            total = Math.max(0, total - 1_000);
        }
        const result = new Result("Розраховано", hours, total);
        this.receipts.save(request, result);
        return result;
    }
}

module.exports = { Request, Result, ParkingAvailability, SubscriptionRegistry, ParkingRepository, ParkingService };
