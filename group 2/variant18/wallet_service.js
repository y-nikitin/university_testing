"use strict";

class Request {
    constructor(amount, dailyTransferred, internalNetwork, trustedRecipient) {
        this.amount = amount;
        this.dailyTransferred = dailyTransferred;
        this.internalNetwork = internalNetwork;
        this.trustedRecipient = trustedRecipient;
        Object.freeze(this);
    }
}

class Result {
    constructor(status, amount = 0, fee = 0, balance = 0, dailyTransferred = 0) {
        this.status = status;
        this.amount = amount;
        this.fee = fee;
        this.balance = balance;
        this.dailyTransferred = dailyTransferred;
        Object.freeze(this);
    }
}

class WalletState {
    currentStatus() {
        throw new Error("Потрібно надати залежність: WalletState");
    }
}

class BalanceProvider {
    currentBalance() {
        throw new Error("Потрібно надати залежність: BalanceProvider");
    }
}

class TransferRepository {
    save(request, result) {
        throw new Error("Потрібно надати залежність: TransferRepository");
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

class WalletService {
    constructor(wallet, balances, transfers) {
        if (wallet == null || balances == null || transfers == null) {
            throw new TypeError("Усі залежності мають бути задані");
        }
        this.wallet = wallet;
        this.balances = balances;
        this.transfers = transfers;
    }

    process(request) {
        if (!(request instanceof Request)) {
            throw new TypeError("Заявку не задано або її формат некоректний");
        }
        checkRange(request.amount, 100, 1_000_000, "Сума переказу");
        checkRange(request.dailyTransferred, 0, 2_000_000, "Добова сума");
        checkFlags(request.internalNetwork, request.trustedRecipient);
        const state = this.wallet.currentStatus();
        if (state === "Заблокований") {
            return new Result("Гаманець заблокований");
        }
        if (state === "Закритий") {
            return new Result("Гаманець закритий");
        }
        if (state !== "Активний") {
            throw new Error("Невідомий стан гаманця");
        }
        const daily = request.dailyTransferred + request.amount;
        if (daily >= 2_000_000) {
            return new Result("Перевищено добовий ліміт");
        }
        let basisPoints;
        if (request.internalNetwork && request.trustedRecipient) {
            basisPoints = 0;
        } else if (request.internalNetwork) {
            basisPoints = 100;
        } else if (request.trustedRecipient) {
            basisPoints = 100;
        } else {
            basisPoints = 200;
        }
        const fee = basisPoints === 0 ? 0 : Math.max(100, Math.floor(request.amount * basisPoints / 10_000));
        const balance = readCount(this.balances.currentBalance(), "Баланс гаманця");
        const debit = request.amount + fee;
        if (balance <= debit) {
            return new Result("Недостатньо коштів");
        }
        const result = new Result("Виконано", request.amount, fee, balance - debit, daily);
        this.transfers.save(request, result);
        return result;
    }
}

module.exports = { Request, Result, WalletState, BalanceProvider, TransferRepository, WalletService };
