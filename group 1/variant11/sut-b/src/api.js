"use strict";
const express = require("express");
const path = require("node:path");
const { connect } = require("./database");
const { StudentRepository, CourseRepository, EnrollmentRepository } = require("./repositories");
const { EnrollmentService, EnrollmentRejectedError, ResourceNotFoundError } = require("./service");
const { Student, Course, EnrollmentRequest } = require("./models");
const { HttpEnrollmentPolicy, EnrollmentPolicyError } = require("./http-enrollment-policy");

function createApp() {
    const app = express();
    app.use(express.json());
    const policy = process.env.ENROLLMENT_POLICY_URL
        ? new HttpEnrollmentPolicy(process.env.ENROLLMENT_POLICY_URL)
        : { isAllowed: async () => true };

    // З’єднання закривається після завершення запиту.
    function route(operation) {
        return async (req, res, next) => {
            let client;
            try {
                client = await connect();
                const students = new StudentRepository(client);
                const courses = new CourseRepository(client);
                const enrollments = new EnrollmentRepository(client);
                const service = new EnrollmentService(students, courses, enrollments, policy);
                await operation(req, res, students, courses, enrollments, service);
            } catch (error) {
                next(error);
            } finally {
                if (client) await client.end().catch(() => {});
            }
        };
    }

    function id(value) {
        const result = Number(value);
        if (!/^[0-9]+$/.test(value) || !Number.isSafeInteger(result) || result <= 0)
            throw new RangeError("Некоректний ідентифікатор");
        return result;
    }

    app.post("/students", route(async (req, res, students) => {
        const { code, age, entryScore, regular = false } = req.body || {};
        if (typeof code !== "string" || !code.trim() || !Number.isSafeInteger(age)
            || !Number.isSafeInteger(entryScore) || typeof regular !== "boolean")
            throw new RangeError("Некоректний слухач");
        const saved = await students.create(new Student(0, code, age, entryScore, regular));
        res.location(`/students/${saved.id}`).status(201).json(saved);
    }));
    app.post("/courses", route(async (req, res, students, courses) => {
        const { code, price, capacity, level = 1 } = req.body || {};
        if (typeof code !== "string" || !code.trim() || ![price, capacity, level].every(Number.isSafeInteger))
            throw new RangeError("Некоректний курс");
        const saved = await courses.create(new Course(0, code, price, capacity, level));
        res.location(`/courses/${saved.id}`).status(201).json(saved);
    }));
    app.post("/enrollments", route(async (req, res, students, courses, enrollments, service) => {
        const { reference, studentId, courseId } = req.body || {};
        const saved = await service.enroll(new EnrollmentRequest(reference, studentId, courseId));
        res.location(`/enrollments/${saved.id}`).status(201).json(saved);
    }));
    app.get("/students/:id", route(async (req, res, students) => {
        const saved = await students.findById(id(req.params.id));
        if (!saved) throw new ResourceNotFoundError("Слухача не знайдено");
        res.json(saved);
    }));
    app.get("/courses/:id", route(async (req, res, students, courses) => {
        const saved = await courses.findById(id(req.params.id));
        if (!saved) throw new ResourceNotFoundError("Курс не знайдено");
        res.json(saved);
    }));
    app.get("/enrollments/:id", route(async (req, res, students, courses, enrollments) => {
        const saved = await enrollments.findById(id(req.params.id));
        if (!saved) throw new ResourceNotFoundError("Заявку не знайдено");
        res.json(saved);
    }));
    app.post("/enrollments/:id/complete", route(async (req, res, students, courses, enrollments, service) => {
        res.json(await service.complete(id(req.params.id)));
    }));
    app.post("/enrollments/:id/cancel", route(async (req, res, students, courses, enrollments, service) => {
        res.json(await service.cancel(id(req.params.id), (req.body || {}).daysBeforeStart));
    }));
    for (const [path, allow] of [["/students", "POST"], ["/students/:id", "GET"],
        ["/courses", "POST"], ["/courses/:id", "GET"], ["/enrollments", "POST"], ["/enrollments/:id", "GET"],
        ["/enrollments/:id/complete", "POST"], ["/enrollments/:id/cancel", "POST"]]) {
        app.all(path, (req, res) => res.set("Allow", allow).status(405)
            .json({ code: "METHOD_NOT_ALLOWED", message: "Метод не підтримується" }));
    }
    app.use(express.static(path.resolve(__dirname, "../../sut-c-ui")));
    app.use((req, res) => res.status(404).json({ code: "NOT_FOUND", message: "Маршрут не знайдено" }));
    app.use((error, req, res, next) => {
        if (res.headersSent) return next(error);
        let status = 500, code = "INTERNAL_ERROR", message = "Внутрішня помилка сервера";
        if (error instanceof ResourceNotFoundError) {
            status = 404; code = "NOT_FOUND"; message = error.message;
        } else if (error instanceof EnrollmentRejectedError) {
            status = 409; code = "ENROLLMENT_CONFLICT"; message = error.message;
        } else if (error instanceof EnrollmentPolicyError) {
            status = 502; code = "POLICY_UNAVAILABLE"; message = "Зовнішня перевірка недоступна";
        } else if (error.code === "23505") {
            status = 409; code = "DUPLICATE_RESOURCE"; message = "Ресурс із таким кодом уже існує";
        } else if (error.code === "23503") {
            status = 409; code = "RESOURCE_CONFLICT"; message = "Конфлікт пов’язаних даних";
        } else if (error instanceof RangeError || error instanceof TypeError || error.type === "entity.parse.failed"
            || ["23514", "23502", "22003"].includes(error.code)) {
            status = 400; code = "INVALID_REQUEST"; message = "Некоректні дані запиту";
        }
        res.status(status).json({ code, message });
    });
    return app;
}

if (require.main === module) {
    const server = createApp().listen(Number(process.env.API_PORT || 8080), "127.0.0.1", () => {
        console.log(`API запущено: http://127.0.0.1:${server.address().port}`);
    });
    for (const signal of ["SIGINT", "SIGTERM"]) process.once(signal, () => server.close());
}
module.exports = { createApp };
