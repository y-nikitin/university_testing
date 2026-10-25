"use strict";
const { PostgreSqlContainer } = require("@testcontainers/postgresql");
const { Client } = require("pg");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { randomUUID } = require("node:crypto");
const { Customer, Parcel, ShipmentRequest, ShipmentStatus: S } = require("../src/models");
const { CustomerRepository, ParcelRepository, ShipmentRepository } = require("../src/repositories");
const { ShippingService, ShipmentRejectedError } = require("../src/service");

jest.setTimeout(120000);
let container, client, customers, parcels, shipments, service;
let shipmentId = 0;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

beforeAll(async () => {
    container = await new PostgreSqlContainer("postgres:17.6-alpine").start();
    client = new Client({ connectionString: container.getConnectionUri() });
    await client.connect();
    await client.query(readFileSync(join(__dirname, "../migrations/V1__create_tables.sql"), "utf8"));
    customers = new CustomerRepository(client);
    parcels = new ParcelRepository(client);
    shipments = new ShipmentRepository(client);
    service = new ShippingService(customers, parcels, shipments, { isAllowed: async () => true });
});
afterAll(async () => {
    try { if (client) await client.end(); }
    finally { if (container) await container.stop(); }
});

const customer = () => customers.create(new Customer(0, randomUUID(), false));
const parcel = () => parcels.create(new Parcel(0, randomUUID(), 1000, 0, 10, 10, 10));
const request = (person, training) => new ShipmentRequest(randomUUID(), person.id, training.id, 100);

const cases = [
    ["Відправлення зберігається у БД", async () => {
        const saved = await service.createShipment(request(await customer(), await parcel()));
        shipmentId = saved.id;
        expect(saved.status).toBe(S.REGISTERED);
        expect(await shipments.findById(saved.id)).toEqual(saved);
    }],
    ["Передача відправлення в доставку", async () => {
        const saved = await service.dispatch(shipmentId);
        expect(saved.status).toBe(S.DISPATCHED);
        expect(await shipments.findById(shipmentId)).toEqual(saved);
    }],
    ["Картка посилки зберігає вагу", async () => {
        const saved = await parcels.create(new Parcel(0, "REGRESSION-PARCEL", 1000, 0, 10, 10, 10));
        expect((await parcels.findById(saved.id)).weightGrams).toBe(1000);
    }],
    ["Картка посилки зберігає оголошену вартість", async () => {
        const saved = await parcels.create(new Parcel(0, "REGRESSION-PARCEL", 2000, 10000, 10, 10, 10));
        expect((await parcels.findById(saved.id)).declaredValue).toBe(10000);
    }],
    ["Відправлення після зовнішнього дозволу", async () => {
        const person = await customer();
        const data = request(person, await parcel());
        const delayed = new ShippingService(customers, parcels, shipments, {
            isAllowed: async () => {
                await delay(Number(process.env.REGRESSION_POLICY_DELAY_MS || 200));
                return true;
            }
        });
        const operation = delayed.createShipment(data);
        operation.catch(() => {});
        try {
            await delay(50);
            const saved = await shipments.findShipments(person.id, S.REGISTERED);
            expect(saved).toHaveLength(1);
            expect(saved[0].reference).toBe(data.reference);
            expect(saved[0].total).toBe(7000);
        } finally { await operation; }
    }],
    ["Вибірка містить два відправлення клієнта", async () => {
        const person = await customer();
        const prefix = randomUUID().slice(0, 8);
        const references = [prefix + "-A", prefix + "-B"];
        const fixtureOrder = process.env.REGRESSION_REVERSE_FIXTURE === "true" ? [...references].reverse() : references;
        for (const reference of fixtureOrder) {
            await service.createShipment(new ShipmentRequest(reference, person.id, (await parcel()).id, 100));
        }
        const result = await client.query("SELECT reference FROM shipments WHERE customer_id=$1", [person.id]);
        expect(result.rows.map(row => row.reference)).toEqual(references);
    }],
    ["Завершення доставки зберігає стан", async () => {
        const saved = await service.createShipment(request(await customer(), await parcel()));
        await service.dispatch(saved.id);
        const delivered = await service.deliver(saved.id);
        expect(delivered.status).toBe(S.DELIVERED);
        expect(await shipments.findById(saved.id)).toEqual(delivered);
    }],
    ["Завершення до відправлення відхиляється", async () => {
        const saved = await service.createShipment(request(await customer(), await parcel()));
        await expect(service.deliver(saved.id)).rejects.toBeInstanceOf(ShipmentRejectedError);
        expect(await shipments.findById(saved.id)).toEqual(saved);
    }],
    ["Зовнішня відмова не створює відправлення", async () => {
        const person = await customer();
        const denied = new ShippingService(customers, parcels, shipments, { isAllowed: async () => false });
        await expect(denied.createShipment(request(person, await parcel()))).rejects.toBeInstanceOf(ShipmentRejectedError);
        expect(await shipments.findShipments(person.id, S.REGISTERED)).toEqual([]);
    }],
    ["Повторне скасування не змінює відправлення", async () => {
        const saved = await service.createShipment(request(await customer(), await parcel()));
        const cancelled = await service.cancel(saved.id);
        await expect(service.cancel(saved.id)).rejects.toBeInstanceOf(ShipmentRejectedError);
        expect(await shipments.findById(saved.id)).toEqual(cancelled);
    }]
];
if (process.env.REGRESSION_REVERSE_TESTS === "true") cases.reverse();
for (const [name, scenario] of cases) test(name, scenario);
