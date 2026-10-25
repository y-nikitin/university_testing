"use strict";
const { PostgreSqlContainer } = require("@testcontainers/postgresql");
const { Client } = require("pg");
const { readFileSync } = require("node:fs");
const { join } = require("node:path");
const { randomUUID } = require("node:crypto");
const { Student, Course, EnrollmentRequest, EnrollmentStatus: S } = require("../src/models");
const { StudentRepository, CourseRepository, EnrollmentRepository } = require("../src/repositories");
const { EnrollmentService, EnrollmentRejectedError } = require("../src/service");

jest.setTimeout(120000);
let container, client, students, courses, enrollments, service;
let enrollmentId = 0;
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));

beforeAll(async () => {
    container = await new PostgreSqlContainer("postgres:17.6-alpine").start();
    client = new Client({ connectionString: container.getConnectionUri() });
    await client.connect();
    await client.query(readFileSync(join(__dirname, "../migrations/V1__create_tables.sql"), "utf8"));
    students = new StudentRepository(client);
    courses = new CourseRepository(client);
    enrollments = new EnrollmentRepository(client);
    service = new EnrollmentService(students, courses, enrollments, { isAllowed: async () => true });
});
afterAll(async () => {
    try { if (client) await client.end(); }
    finally { if (container) await container.stop(); }
});

const student = () => students.create(new Student(0, randomUUID(), 20, 90, false));
const course = () => courses.create(new Course(0, randomUUID(), 100000, 10, 1));
const request = (person, training) => new EnrollmentRequest(randomUUID(), person.id, training.id);

const cases = [
    ["Зарахування зберігається у БД", async () => {
        const saved = await service.enroll(request(await student(), await course()));
        enrollmentId = saved.id;
        expect(saved.status).toBe(S.ENROLLED);
        expect(await enrollments.findById(saved.id)).toEqual(saved);
    }],
    ["Скасування повертає передоплату", async () => {
        const cancelled = await service.cancel(enrollmentId, 7);
        expect(cancelled.status).toBe(S.CANCELLED);
        expect(cancelled.refund).toBe(cancelled.prepaid);
        expect(await enrollments.findById(enrollmentId)).toEqual(cancelled);
    }],
    ["Картка слухача зберігає вік", async () => {
        const saved = await students.create(new Student(0, "REGRESSION-STUDENT", 20, 90, false));
        expect((await students.findById(saved.id)).age).toBe(20);
    }],
    ["Картка слухача зберігає вступний бал", async () => {
        const saved = await students.create(new Student(0, "REGRESSION-STUDENT", 25, 85, true));
        expect((await students.findById(saved.id)).entryScore).toBe(85);
    }],
    ["Зарахування після зовнішнього дозволу", async () => {
        const training = await course();
        const data = request(await student(), training);
        const delayed = new EnrollmentService(students, courses, enrollments, {
            isAllowed: async () => {
                await delay(Number(process.env.REGRESSION_POLICY_DELAY_MS || 200));
                return true;
            }
        });
        const operation = delayed.enroll(data);
        operation.catch(() => {});
        try {
            await delay(50);
            const saved = await enrollments.findEnrollments(training.id, S.ENROLLED);
            expect(saved).toHaveLength(1);
            expect(saved[0].reference).toBe(data.reference);
            expect(saved[0].total).toBe(100000);
        } finally { await operation; }
    }],
    ["Вибірка містить дві заявки курсу", async () => {
        const training = await course();
        const prefix = randomUUID().slice(0, 8);
        const references = [prefix + "-A", prefix + "-B"];
        const fixtureOrder = process.env.REGRESSION_REVERSE_FIXTURE === "true" ? [...references].reverse() : references;
        for (const reference of fixtureOrder) {
            await service.enroll(new EnrollmentRequest(reference, (await student()).id, training.id));
        }
        const result = await client.query("SELECT reference FROM enrollments WHERE course_id=$1", [training.id]);
        expect(result.rows.map(row => row.reference)).toEqual(references);
    }],
    ["Завершення зберігає стан заявки", async () => {
        const saved = await service.enroll(request(await student(), await course()));
        const completed = await service.complete(saved.id);
        expect(completed.status).toBe(S.COMPLETED);
        expect(await enrollments.findById(saved.id)).toEqual(completed);
    }],
    ["Повторне зарахування відхиляється", async () => {
        const person = await student(), training = await course();
        const saved = await service.enroll(request(person, training));
        await expect(service.enroll(request(person, training))).rejects.toBeInstanceOf(EnrollmentRejectedError);
        expect(await enrollments.findById(saved.id)).toEqual(saved);
        expect(await enrollments.countOccupied(training.id)).toBe(1);
    }],
    ["Зовнішня відмова не створює заявку", async () => {
        const training = await course();
        const denied = new EnrollmentService(students, courses, enrollments, { isAllowed: async () => false });
        await expect(denied.enroll(request(await student(), training))).rejects.toBeInstanceOf(EnrollmentRejectedError);
        expect(await enrollments.findEnrollments(training.id, S.ENROLLED)).toEqual([]);
    }],
    ["Повторне скасування не змінює заявку", async () => {
        const saved = await service.enroll(request(await student(), await course()));
        const cancelled = await service.cancel(saved.id, 7);
        await expect(service.cancel(saved.id, 7)).rejects.toBeInstanceOf(EnrollmentRejectedError);
        expect(await enrollments.findById(saved.id)).toEqual(cancelled);
    }]
];
if (process.env.REGRESSION_REVERSE_TESTS === "true") cases.reverse();
for (const [name, scenario] of cases) test(name, scenario);
