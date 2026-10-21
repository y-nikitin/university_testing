package hotel.flaky;

import hotel.Database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.UUID;

/**
 * Допоміжний клас для лекційних прикладів (кейси 1 і 4).
 * Використовує ту саму локальну PostgreSQL, що й BookingIntegrationTest
 * (змінні DB_URL, DB_USER, DB_PASSWORD або database-test.properties).
 */
public final class FlakyDb {
    private FlakyDb() {}

    public static Connection connect() throws Exception {
        return Database.fromEnvironment().connect();
    }

    /** Створює окрему схему, застосовує міграції V1 і V2 та перемикає на неї з'єднання. */
    public static String createSchema(Connection connection, String prefix) throws Exception {
        String schema = prefix + "_" + UUID.randomUUID().toString().replace("-", "");
        try (var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA " + schema);
        }
        connection.setSchema(schema);
        try (var sql = connection.createStatement()) {
            sql.execute(Files.readString(Path.of("migrations/V1__create_tables.sql")));
            sql.execute(Files.readString(Path.of("migrations/V2__seed_statuses.sql")));
        }
        return schema;
    }

    /** Видаляє тільки свою схему; ROLLBACK — технічне очищення після можливої помилки міграції. */
    public static void dropSchema(Connection connection, String schema) throws Exception {
        if (connection == null) return;
        try {
            try (var sql = connection.createStatement()) { sql.execute("ROLLBACK"); }
            connection.setSchema("public");
            if (schema != null) {
                try (var sql = connection.createStatement()) {
                    sql.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        } finally {
            connection.close();
        }
    }
}
