"use strict";

class Request {
    constructor(age, days, premium, insured) {
        this.age = age;
        this.days = days;
        this.premium = premium;
        this.insured = insured;
        Object.freeze(this);
    }
}

class Result {
    constructor(status, rental = 0, ageSurcharge = 0, insurance = 0, deposit = 0, total = 0) {
        this.status = status;
        this.rental = rental;
        this.ageSurcharge = ageSurcharge;
        this.insurance = insurance;
        this.deposit = deposit;
        this.total = total;
        Object.freeze(this);
    }
}

class FleetAvailability {
    isAvailable() {
        throw new Error("Потрібно надати залежність: FleetAvailability");
    }
}

class DriverHistory {
    hasIncident() {
        throw new Error("Потрібно надати залежність: DriverHistory");
    }
}

class RentalRepository {
    save(request, result) {
        throw new Error("Потрібно надати залежність: RentalRepository");
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

class CarRentalService {
    constructor(fleet, history, rentals) {
        if (fleet == null || history == null || rentals == null) {
            throw new TypeError("Усі залежності мають бути задані");
        }
        this.fleet = fleet;
        this.history = history;
        this.rentals = rentals;
    }

    process(request) {
        if (!(request instanceof Request)) {
            throw new TypeError("Заявку не задано або її формат некоректний");
        }
        checkRange(request.age, 21, 74, "Вік водія");
        checkRange(request.days, 1, 30, "Строк оренди");
        checkFlags(request.premium, request.insured);
        if (!readFlag(this.fleet.isAvailable(), "Доступність автомобіля")) {
            return new Result("Немає автомобіля");
        }
        const young = request.age < 25;
        if ((young && request.premium) && readFlag(this.history.hasIncident(), "Історія водія")) {
            return new Result("Відмова за історією");
        }
        const base = request.days * (request.premium ? 90_000 : 50_000);
        const rental = request.days > 7 ? base - Math.floor(base * 10 / 100) : base;
        const ageSurcharge = young ? request.days * 10_000 : 0;
        const insurance = request.insured ? request.days * 8_000 : 0;
        const deposit = request.insured ? 100_000 : 200_000;
        const result = new Result("Оформлено", rental, ageSurcharge, insurance, deposit,
            rental + ageSurcharge + insurance + deposit);
        this.rentals.save(request, result);
        return result;
    }
}

module.exports = { Request, Result, FleetAvailability, DriverHistory, RentalRepository, CarRentalService };
