"use strict";

class Request {
    constructor(purchaseAmount, pointsToSpend, gold, promotion) {
        this.purchaseAmount = purchaseAmount;
        this.pointsToSpend = pointsToSpend;
        this.gold = gold;
        this.promotion = promotion;
        Object.freeze(this);
    }
}

class Result {
    constructor(status, amountDue = 0, pointsSpent = 0, pointsEarned = 0, balance = 0) {
        this.status = status;
        this.amountDue = amountDue;
        this.pointsSpent = pointsSpent;
        this.pointsEarned = pointsEarned;
        this.balance = balance;
        Object.freeze(this);
    }
}

class MemberStatus {
    isActive() {
        throw new Error("Потрібно надати залежність: MemberStatus");
    }
}

class PointsBalance {
    currentBalance() {
        throw new Error("Потрібно надати залежність: PointsBalance");
    }
}

class LoyaltyRepository {
    save(request, result) {
        throw new Error("Потрібно надати залежність: LoyaltyRepository");
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

class LoyaltyService {
    constructor(members, points, operations) {
        if (members == null || points == null || operations == null) {
            throw new TypeError("Усі залежності мають бути задані");
        }
        this.members = members;
        this.points = points;
        this.operations = operations;
    }

    process(request) {
        if (!(request instanceof Request)) {
            throw new TypeError("Заявку не задано або її формат некоректний");
        }
        checkRange(request.purchaseAmount, 100, 1_000_000, "Сума покупки");
        checkRange(request.pointsToSpend, 0, 4_999, "Бали для списання");
        checkFlags(request.gold, request.promotion);
        if (!readFlag(this.members.isActive(), "Активність рахунку")) {
            return new Result("Рахунок неактивний");
        }
        const balance = readCount(this.points.currentBalance(), "Баланс балів");
        if (request.pointsToSpend > balance) {
            return new Result("Недостатньо балів");
        }
        if (request.pointsToSpend >= Math.floor(request.purchaseAmount / 200)) {
            return new Result("Перевищено частку оплати");
        }
        const amountDue = request.purchaseAmount - request.pointsToSpend * 100;
        let multiplier;
        if (request.gold && request.promotion) {
            multiplier = 4;
        } else if (request.gold || request.promotion) {
            multiplier = 2;
        } else {
            multiplier = 1;
        }
        const earned = Math.floor(amountDue / 10_000) * multiplier;
        const updatedBalance = readCount(balance - request.pointsToSpend + earned, "Новий баланс балів");
        const result = new Result("Виконано", amountDue, request.pointsToSpend, earned, updatedBalance);
        this.operations.save(request, result);
        return result;
    }
}

module.exports = { Request, Result, MemberStatus, PointsBalance, LoyaltyRepository, LoyaltyService };
