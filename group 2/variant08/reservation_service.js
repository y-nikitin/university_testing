"use strict";

class Request {
    constructor(guests, hoursBefore, terrace, celebration) {
        this.guests = guests;
        this.hoursBefore = hoursBefore;
        this.terrace = terrace;
        this.celebration = celebration;
        Object.freeze(this);
    }
}

class Result {
    constructor(status, guests = 0, zone = "", deposit = 0) {
        this.status = status;
        this.guests = guests;
        this.zone = zone;
        this.deposit = deposit;
        Object.freeze(this);
    }
}

class TerraceAvailability {
    isOpen() {
        throw new Error("Потрібно надати залежність: TerraceAvailability");
    }
}

class SeatAvailability {
    freeSeats() {
        throw new Error("Потрібно надати залежність: SeatAvailability");
    }
}

class ReservationRepository {
    save(request, result) {
        throw new Error("Потрібно надати залежність: ReservationRepository");
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

class ReservationService {
    constructor(terrace, seats, reservations) {
        if (terrace == null || seats == null || reservations == null) {
            throw new TypeError("Усі залежності мають бути задані");
        }
        this.terrace = terrace;
        this.seats = seats;
        this.reservations = reservations;
    }

    process(request) {
        if (!(request instanceof Request)) {
            throw new TypeError("Заявку не задано або її формат некоректний");
        }
        checkRange(request.guests, 1, 11, "Кількість гостей");
        checkRange(request.hoursBefore, 2, 720, "Години до візиту");
        checkFlags(request.terrace, request.celebration);
        if (request.terrace && !readFlag(this.terrace.isOpen(), "Доступність тераси")) {
            return new Result("Тераса недоступна");
        }
        const available = readCount(this.seats.freeSeats(), "Вільні місця");
        if (available < request.guests) {
            return new Result("Недостатньо місць");
        }
        const base = request.guests >= 6 ? request.guests * 10_000 : 0;
        let options;
        if (request.terrace && request.celebration) {
            options = 13_000;
        } else if (request.terrace) {
            options = 5_000;
        } else if (request.celebration) {
            options = 8_000;
        } else {
            options = 0;
        }
        const urgent = request.hoursBefore <= 24 ? 3_000 : 0;
        const result = new Result("Підтверджено", request.guests,
            request.terrace ? "Тераса" : "Зал", base + options + urgent);
        this.reservations.save(request, result);
        return result;
    }
}

module.exports = { Request, Result, TerraceAvailability, SeatAvailability, ReservationRepository, ReservationService };
