"use strict";
const { ShippingPolicy } = require("./shipping-policy");
class ShippingPolicyError extends Error {
    constructor(message, options) { super(message, options); this.name = "ShippingPolicyError"; }
}
class HttpShippingPolicy extends ShippingPolicy {
    constructor(baseUrl, timeoutMs = 2000) {
        super();
        let address;
        try {
            if (typeof baseUrl !== "string") throw new TypeError();
            address = new URL(baseUrl);
            if (!["http:", "https:"].includes(address.protocol) || address.username || address.password
                || address.pathname !== "/" || address.search || address.hash || address.port === "0") throw new TypeError();
        } catch {
            throw new TypeError("Потрібна базова HTTP(S)-адреса без шляху, параметрів та облікових даних");
        }
        if (!Number.isSafeInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 2147483647)
            throw new RangeError("Таймаут має бути цілим числом мілісекунд від 1 до 2147483647");
        this.endpoint = new URL("/shipping-policy", address).href;
        this.timeoutMs = timeoutMs;
    }
    async isAllowed(customer, parcel, distanceKm) {
        const body = JSON.stringify({ customerId: customer.id, parcelId: parcel.id, distanceKm });
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.timeoutMs);
        try {
            const response = await fetch(this.endpoint, {
                method: "POST", headers: { "Content-Type": "application/json", "Accept": "application/json" },
                body, redirect: "manual", signal: controller.signal
            });
            if (response.status !== 200) {
                if (response.body) await response.body.cancel();
                throw new ShippingPolicyError("Помилка HTTP-політики: " + response.status);
            }
            const result = await response.json();
            if (!result || Array.isArray(result) || typeof result !== "object" || typeof result.allowed !== "boolean")
                throw new ShippingPolicyError("Відповідь політики має містити логічне поле allowed");
            return result.allowed;
        } catch (error) {
            if (error instanceof ShippingPolicyError) throw error;
            throw new ShippingPolicyError("HTTP-перевірка недоступна, перевищено час очікування або отримано некоректний JSON", { cause: error });
        } finally {
            clearTimeout(timer);
        }
    }
}
module.exports = { HttpShippingPolicy, ShippingPolicyError };
