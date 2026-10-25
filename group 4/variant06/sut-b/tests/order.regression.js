"use strict";
const { PostgreSqlContainer } = require("@testcontainers/postgresql");
const { Client } = require("pg");
const { readFileSync, readdirSync } = require("node:fs");
const { join } = require("node:path");
const { randomUUID } = require("node:crypto");
const { OrderRequest, OrderStatus: S } = require("../src/models");
const { ProductRepository, OrderRepository } = require("../src/repositories");
const { OrderService, OrderRejectedError } = require("../src/service");

jest.setTimeout(120000);
let container, client, products, orders, service;
let orderId = 0;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

beforeAll(async () => {
    container = await new PostgreSqlContainer("postgres:17.6-alpine").start();
    client = new Client({ connectionString: container.getConnectionUri() });
    await client.connect();
    const migrations = join(__dirname, "../migrations");
    for (const file of readdirSync(migrations).filter(name => /^V.*__.*[.]sql$/.test(name)).sort()) {
        await client.query(readFileSync(join(migrations, file), "utf8"));
    }
    products = new ProductRepository(client);
    orders = new OrderRepository(client);
    service = new OrderService(products, orders, { isAllowed: async () => true });
});
afterAll(async () => {
    try { if (client) await client.end(); }
    finally { if (container) await container.stop(); }
});
const product = () => products.create(randomUUID(), 10000, 5, false);
const request = item => new OrderRequest(randomUUID(), item.id, 1, false);

const cases = [
    ["Замовлення зберігається у БД", async () => {
        const saved = await service.placeOrder(request(await product()));
        orderId = saved.id;
        expect(saved.status).toBe(S.CONFIRMED);
        expect(await orders.findById(saved.id)).toEqual(saved);
    }],
    ["Повернення товару змінює статус", async () => {
        const returned = await service.returnOrder(orderId, 0, false);
        expect(returned.status).toBe(S.RETURNED);
        expect(await orders.findById(orderId)).toEqual(returned);
    }],
    ["Картка товару зберігає залишок", async () => {
        const saved = await products.create("REGRESSION-PRODUCT", 10000, 5, false);
        expect((await products.findById(saved.id)).stock).toBe(5);
    }],
    ["Картка товару зберігає ціну", async () => {
        const saved = await products.create("REGRESSION-PRODUCT", 15000, 3, false);
        expect((await products.findById(saved.id)).unitPrice).toBe(15000);
    }],
    ["Замовлення після зовнішнього дозволу", async () => {
        const item = await product();
        const data = request(item);
        const delayed = new OrderService(products, orders, {
            isAllowed: async () => {
                await delay(Number(process.env.REGRESSION_POLICY_DELAY_MS || 200));
                return true;
            }
        });
        const operation = delayed.placeOrder(data);
        operation.catch(() => {});
        try {
            await delay(50);
            const saved = await orders.findOrders(item.id, S.CONFIRMED);
            expect(saved).toHaveLength(1);
            expect(saved[0].reference).toBe(data.reference);
            expect(saved[0].total).toBe(10000);
        } finally { await operation; }
    }],
    ["Вибірка містить два замовлення товару", async () => {
        const item = await product();
        const prefix = randomUUID().slice(0, 8);
        const references = [prefix + "-A", prefix + "-B"];
        const fixtureOrder = process.env.REGRESSION_REVERSE_FIXTURE === "true" ? [...references].reverse() : references;
        for (const reference of fixtureOrder) await service.placeOrder(new OrderRequest(reference, item.id, 1, false));
        const result = await client.query("SELECT o.reference FROM orders o JOIN order_items i ON i.order_id=o.id WHERE i.product_id=$1", [item.id]);
        expect(result.rows.map(row => row.reference)).toEqual(references);
    }],
    ["Замовлення зберігає суму й позицію", async () => {
        const item = await product();
        const saved = await service.placeOrder(request(item));
        const stored = await orders.findById(saved.id);
        expect(stored.total).toBe(10000);
        expect(stored.item.productId).toBe(item.id);
        expect(stored.item.quantity).toBe(1);
        expect(stored.item.unitPrice).toBe(10000);
    }],
    ["Нестача товару не створює замовлення", async () => {
        const item = await products.create(randomUUID(), 10000, 0, false);
        await expect(service.placeOrder(request(item))).rejects.toBeInstanceOf(OrderRejectedError);
        expect(await orders.findOrders(item.id, S.CONFIRMED)).toEqual([]);
    }],
    ["Зовнішня відмова не створює замовлення", async () => {
        const item = await product();
        const denied = new OrderService(products, orders, { isAllowed: async () => false });
        await expect(denied.placeOrder(request(item))).rejects.toBeInstanceOf(OrderRejectedError);
        expect(await orders.findOrders(item.id, S.CONFIRMED)).toEqual([]);
    }],
    ["Повторне повернення не змінює замовлення", async () => {
        const saved = await service.placeOrder(request(await product()));
        const returned = await service.returnOrder(saved.id, 0, false);
        await expect(service.returnOrder(saved.id, 0, false)).rejects.toBeInstanceOf(OrderRejectedError);
        expect(await orders.findById(saved.id)).toEqual(returned);
    }]
];
if (process.env.REGRESSION_REVERSE_TESTS === "true") cases.reverse();
for (const [name, scenario] of cases) test(name, scenario);
