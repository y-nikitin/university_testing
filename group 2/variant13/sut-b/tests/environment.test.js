const { connect } = require("../src/database");
test("Підключення до тестової бази даних", async () => {
    const client = await connect();
    try { expect((await client.query("SELECT 1 AS value")).rows[0].value).toBe(1); }
    finally { await client.end(); }
});
