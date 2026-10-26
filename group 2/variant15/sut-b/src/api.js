"use strict";
const express = require("express");
const path = require("node:path");
const { connect } = require("./database");
const { CustomerRepository, ParcelRepository, ShipmentRepository } = require("./repositories");
const { ShippingService, ShipmentRejectedError, ResourceNotFoundError } = require("./service");
const { Customer, Parcel, ShipmentRequest } = require("./models");
const { HttpShippingPolicy, ShippingPolicyError } = require("./http-shipping-policy");

function createApp() {
    const app = express();
    app.use(express.json());
    const policy = process.env.SHIPPING_POLICY_URL
        ? new HttpShippingPolicy(process.env.SHIPPING_POLICY_URL)
        : { isAllowed: async () => true };

    // З’єднання закривається після завершення запиту.
    function route(operation) {
        return async (req, res, next) => {
            let client;
            try {
                client = await connect();
                const customers = new CustomerRepository(client);
                const parcels = new ParcelRepository(client);
                const shipments = new ShipmentRepository(client);
                const service = new ShippingService(customers, parcels, shipments, policy);
                await operation(req, res, customers, parcels, shipments, service);
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

    app.post("/customers", route(async (req, res, customers) => {
        const { code, regular = false } = req.body || {};
        if (typeof code !== "string" || !code.trim() || typeof regular !== "boolean")
            throw new RangeError("Некоректний клієнт");
        const saved = await customers.create(new Customer(0, code, regular));
        res.location(`/customers/${saved.id}`).status(201).json(saved);
    }));
    app.get("/customers/:id", route(async (req, res, customers) => {
        const saved = await customers.findById(id(req.params.id));
        if (!saved) throw new ResourceNotFoundError("Клієнта не знайдено");
        res.json(saved);
    }));
    app.post("/parcels", route(async (req, res, customers, parcels) => {
        const { code, weightGrams, declaredValue, lengthCm = 10, widthCm = 10, heightCm = 10 } = req.body || {};
        if (typeof code !== "string" || !code.trim()
            || ![weightGrams, declaredValue, lengthCm, widthCm, heightCm].every(Number.isSafeInteger))
            throw new RangeError("Некоректна посилка");
        const saved = await parcels.create(new Parcel(0, code, weightGrams, declaredValue, lengthCm, widthCm, heightCm));
        res.location(`/parcels/${saved.id}`).status(201).json(saved);
    }));
    app.get("/parcels/:id", route(async (req, res, customers, parcels) => {
        const saved = await parcels.findById(id(req.params.id));
        if (!saved) throw new ResourceNotFoundError("Посилку не знайдено");
        res.json(saved);
    }));
    app.post("/shipments", route(async (req, res, customers, parcels, shipments, service) => {
        const { reference, customerId, parcelId, distanceKm, urgent = false, pickup = false } = req.body || {};
        const saved = await service.createShipment(new ShipmentRequest(reference, customerId, parcelId, distanceKm, urgent, pickup));
        res.location(`/shipments/${saved.id}`).status(201).json(saved);
    }));
    app.get("/shipments/:id", route(async (req, res, customers, parcels, shipments) => {
        const saved = await shipments.findById(id(req.params.id));
        if (!saved) throw new ResourceNotFoundError("Відправлення не знайдено");
        res.json(saved);
    }));
    app.post("/shipments/:id/dispatch", route(async (req, res, customers, parcels, shipments, service) => {
        res.json(await service.dispatch(id(req.params.id)));
    }));
    app.post("/shipments/:id/deliver", route(async (req, res, customers, parcels, shipments, service) => {
        res.json(await service.deliver(id(req.params.id)));
    }));
    app.post("/shipments/:id/cancel", route(async (req, res, customers, parcels, shipments, service) => {
        res.json(await service.cancel(id(req.params.id)));
    }));
    for (const [path, allow] of [["/customers", "POST"], ["/customers/:id", "GET"],
        ["/parcels", "POST"], ["/parcels/:id", "GET"], ["/shipments", "POST"], ["/shipments/:id", "GET"],
        ["/shipments/:id/dispatch", "POST"], ["/shipments/:id/deliver", "POST"], ["/shipments/:id/cancel", "POST"]]) {
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
        } else if (error instanceof ShipmentRejectedError) {
            status = 409; code = "SHIPMENT_CONFLICT"; message = error.message;
        } else if (error instanceof ShippingPolicyError) {
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
