"use strict";

class Request {
    constructor(users, months, nonprofitClaimed, autoRenew) {
        this.users = users;
        this.months = months;
        this.nonprofitClaimed = nonprofitClaimed;
        this.autoRenew = autoRenew;
        Object.freeze(this);
    }
}

class Result {
    constructor(status, total = 0, restorationFee = 0, subscriptionState = null) {
        this.status = status;
        this.total = total;
        this.restorationFee = restorationFee;
        this.subscriptionState = subscriptionState;
        Object.freeze(this);
    }
}

class SubscriptionState {
    currentStatus() {
        throw new Error("Потрібно надати відомості про стан підписки");
    }
}

class NonprofitRegistry {
    isEligible() {
        throw new Error("Потрібно надати підтвердження неприбутковості");
    }
}

class BillingRepository {
    save(request, result) {
        throw new Error("Потрібно надати спосіб фіксації продовження");
    }
}

class SubscriptionService {
    constructor(subscriptions, nonprofits, billing) {
        if (subscriptions == null || nonprofits == null || billing == null) {
            throw new TypeError("Усі залежності мають бути задані");
        }
        this.subscriptions = subscriptions;
        this.nonprofits = nonprofits;
        this.billing = billing;
    }

    process(request) {
        if (!(request instanceof Request)) {
            throw new TypeError("Заявку не задано або її формат некоректний");
        }
        this.checkRange(request.users, 1, 49, "Кількість користувачів");
        this.checkRange(request.months, 1, 12, "Строк продовження");
        if (typeof request.nonprofitClaimed !== "boolean" || typeof request.autoRenew !== "boolean") {
            throw new TypeError("Ознаки пільг мають бути логічними значеннями");
        }
        const state = this.subscriptions.currentStatus();
        if (!["Активна", "Прострочена", "Заблокована"].includes(state)) {
            throw new Error("Невідомий стан підписки");
        }
        if (state === "Заблокована") {
            return new Result("Підписку заблоковано", 0, 0, state);
        }
        let nonprofit = false;
        if (request.nonprofitClaimed) {
            nonprofit = this.nonprofits.isEligible();
            if (typeof nonprofit !== "boolean") {
                throw new Error("Некоректні відомості про неприбутковість");
            }
        }
        let discount;
        if (nonprofit && request.autoRenew) {
            discount = 20;
        } else if (nonprofit) {
            discount = 20;
        } else if (request.autoRenew) {
            discount = 5;
        } else {
            discount = 0;
        }
        const base = request.users * request.months * 20_000;
        let total = base - Math.floor(base * discount / 100);
        if (request.months >= 11) {
            total = Math.max(0, total - 10_000);
        }
        const restorationFee = state === "Прострочена" ? 5_000 : 0;
        const result = new Result("Продовжено", total + restorationFee, restorationFee, "Активна");
        this.billing.save(request, result);
        return result;
    }

    checkRange(value, minimum, maximum, field) {
        if (!Number.isInteger(value) || value < minimum || value > maximum) {
            throw new RangeError("Недопустиме значення: " + field);
        }
    }
}

module.exports = { Request, Result, SubscriptionState, NonprofitRegistry, BillingRepository, SubscriptionService };
