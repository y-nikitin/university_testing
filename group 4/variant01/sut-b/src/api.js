"use strict";
const express = require("express");
const { connect } = require("./database");
const { ProductRepository, OrderRepository } = require("./repositories");
const { OrderService, OrderRejectedError, ResourceNotFoundError } = require("./service");
const { OrderRequest } = require("./models");
const { HttpOrderPolicy, OrderPolicyError } = require("./http-order-policy");

function createApp() {
    const app = express();
    app.use(express.json());
    const policy = process.env.ORDER_POLICY_URL
        ? new HttpOrderPolicy(process.env.ORDER_POLICY_URL)
        : { isAllowed: async () => true };

    // Кожна операція отримує окреме з’єднання, включно з транзакцією замовлення.
    function route(operation) {
        return async (req, res, next) => {
            let client;
            try {
                client = await connect();
                const products = new ProductRepository(client);
                const orders = new OrderRepository(client);
                const service = new OrderService(products, orders, policy);
                await operation(req, res, products, orders, service);
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

    app.post("/products", route(async (req, res, products) => {
        const { sku, unitPrice, stock, onSale = false } = req.body || {};
        if (typeof sku !== "string" || !sku.trim() || sku.length > 40
            || !Number.isSafeInteger(unitPrice) || unitPrice < 100 || unitPrice > 1000000
            || !Number.isSafeInteger(stock) || stock < 0 || stock > 2147483647 || typeof onSale !== "boolean")
            throw new RangeError("Некоректний товар");
        const product = await products.create(sku, unitPrice, stock, onSale);
        res.location(`/products/${product.id}`).status(201).json(product);
    }));
    app.get("/products/:id", route(async (req, res, products) => {
        const product = await products.findById(id(req.params.id));
        if (!product) throw new ResourceNotFoundError("Товар не знайдено");
        res.json(product);
    }));
    app.post("/orders", route(async (req, res, products, orders, service) => {
        const { reference, productId, quantity, premium = false } = req.body || {};
        const order = await service.placeOrder(new OrderRequest(reference, productId, quantity, premium));
        res.location(`/orders/${order.id}`).status(201).json(order);
    }));
    app.get("/orders/:id", route(async (req, res, products, orders) => {
        const order = await orders.findById(id(req.params.id));
        if (!order) throw new ResourceNotFoundError("Замовлення не знайдено");
        res.json(order);
    }));
    app.post("/orders/:id/return", route(async (req, res, products, orders, service) => {
        const { daysSincePurchase, packagingOpen = false } = req.body || {};
        res.json(await service.returnOrder(id(req.params.id), daysSincePurchase, packagingOpen));
    }));
    for (const [path, allow] of [["/products", "POST"], ["/products/:id", "GET"],
        ["/orders", "POST"], ["/orders/:id", "GET"], ["/orders/:id/return", "POST"]]) {
        app.all(path, (req, res) => res.set("Allow", allow).status(405)
            .json({ code: "METHOD_NOT_ALLOWED", message: "Метод не підтримується" }));
    }
    app.use((req, res) => res.status(404).json({ code: "NOT_FOUND", message: "Маршрут не знайдено" }));
    app.use((error, req, res, next) => {
        if (res.headersSent) return next(error);
        let status = 500, code = "INTERNAL_ERROR", message = "Внутрішня помилка сервера";
        if (error instanceof ResourceNotFoundError) {
            status = 404; code = "NOT_FOUND"; message = error.message;
        } else if (error instanceof OrderRejectedError) {
            status = 409; code = "ORDER_CONFLICT"; message = error.message;
        } else if (error instanceof OrderPolicyError) {
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
