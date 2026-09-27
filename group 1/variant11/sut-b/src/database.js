"use strict";
async function connect() {
    const { Client } = require("pg");
    const client = new Client({
        host: process.env.DB_HOST || "localhost", port: Number(process.env.DB_PORT || 5432),
        database: process.env.DB_NAME || "courses_variant11_test", user: process.env.DB_USER || "courses_student",
        password: process.env.DB_PASSWORD || ""
    });
    try { await client.connect(); return client; }
    catch (error) { await client.end().catch(() => {}); throw error; }
}
module.exports = { connect };
