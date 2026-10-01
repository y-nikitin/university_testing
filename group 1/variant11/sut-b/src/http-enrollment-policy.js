"use strict";
const { EnrollmentPolicy } = require("./enrollment-policy");
class EnrollmentPolicyError extends Error {
    constructor(message, options) { super(message, options); this.name = "EnrollmentPolicyError"; }
}
class HttpEnrollmentPolicy extends EnrollmentPolicy {
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
        this.endpoint = new URL("/enrollment-policy", address).href;
        this.timeoutMs = timeoutMs;
    }
    async isAllowed(student, course) {
        const body = JSON.stringify({ studentId: student.id, courseId: course.id });
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.timeoutMs);
        try {
            const response = await fetch(this.endpoint, {
                method: "POST", headers: { "Content-Type": "application/json", "Accept": "application/json" },
                body, redirect: "manual", signal: controller.signal
            });
            if (response.status !== 200) {
                if (response.body) await response.body.cancel();
                throw new EnrollmentPolicyError("Помилка HTTP-політики: " + response.status);
            }
            const result = await response.json();
            if (!result || Array.isArray(result) || typeof result !== "object" || typeof result.allowed !== "boolean")
                throw new EnrollmentPolicyError("Відповідь політики має містити логічне поле allowed");
            return result.allowed;
        } catch (error) {
            if (error instanceof EnrollmentPolicyError) throw error;
            throw new EnrollmentPolicyError("HTTP-перевірка недоступна, перевищено час очікування або отримано некоректний JSON", { cause: error });
        } finally {
            clearTimeout(timer);
        }
    }
}
module.exports = { HttpEnrollmentPolicy, EnrollmentPolicyError };
