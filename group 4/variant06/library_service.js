"use strict";

class Request {
    constructor(days, currentLoans, referenceBook, renewal) {
        this.days = days;
        this.currentLoans = currentLoans;
        this.referenceBook = referenceBook;
        this.renewal = renewal;
        Object.freeze(this);
    }
}

class Result {
    constructor(status, days = 0, fee = 0, loanCount = 0) {
        this.status = status;
        this.days = days;
        this.fee = fee;
        this.loanCount = loanCount;
        Object.freeze(this);
    }
}

class CatalogAvailability {
    availableCopies() {
        throw new Error("Потрібно надати залежність: CatalogAvailability");
    }
}

class HoldRegistry {
    hasWaitingReader() {
        throw new Error("Потрібно надати залежність: HoldRegistry");
    }
}

class LoanRepository {
    save(request, result) {
        throw new Error("Потрібно надати залежність: LoanRepository");
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

class LibraryService {
    constructor(catalog, holds, loans) {
        if (catalog == null || holds == null || loans == null) {
            throw new TypeError("Усі залежності мають бути задані");
        }
        this.catalog = catalog;
        this.holds = holds;
        this.loans = loans;
    }

    process(request) {
        if (!(request instanceof Request)) {
            throw new TypeError("Заявку не задано або її формат некоректний");
        }
        checkRange(request.days, 1, 29, "Строк позики");
        checkRange(request.currentLoans, 0, 10, "Кількість позик");
        checkFlags(request.referenceBook, request.renewal);
        if (request.renewal && request.currentLoans === 0) {
            throw new RangeError("Немає чинної позики для продовження");
        }
        if (!request.renewal && request.currentLoans >= 5) {
            return new Result("Ліміт позик");
        }
        if (request.referenceBook && (request.renewal && request.days > 7)) {
            return new Result("Обмеження довідкового видання");
        }
        if (request.renewal) {
            if (readFlag(this.holds.hasWaitingReader(), "Черга читачів")) {
                return new Result("Є черга");
            }
        } else {
            const copies = readCount(this.catalog.availableCopies(), "Доступні примірники");
            if (copies === 0) {
                return new Result("Немає примірника");
            }
        }
        const fee = Math.max(0, request.days - 15) * 100;
        const count = request.renewal ? request.currentLoans : request.currentLoans + 1;
        const result = new Result(request.renewal ? "Продовжено" : "Видано", request.days, fee, count);
        this.loans.save(request, result);
        return result;
    }
}

module.exports = { Request, Result, CatalogAvailability, HoldRegistry, LoanRepository, LibraryService };
