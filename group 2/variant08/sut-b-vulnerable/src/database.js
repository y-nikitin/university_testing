"use strict";
async function connect() {
    const { Client } = require("pg");
    const client = new Client({
        host: process.env.SECURITY_DB_HOST || "localhost", port: Number(process.env.SECURITY_DB_PORT || 55433),
        database: process.env.SECURITY_DB_NAME || "delivery_variant08_security", user: process.env.SECURITY_DB_USER || "delivery_student",
        password: process.env.SECURITY_DB_PASSWORD || ""
    });
    try { await client.connect(); return client; }
    catch (error) { await client.end().catch(() => {}); throw error; }
}
module.exports = { connect };
