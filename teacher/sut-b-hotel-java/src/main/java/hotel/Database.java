package hotel;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public record Database(String url, String user, String password) {
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    public static Database fromEnvironment() throws IOException {
        var settings = new Properties();
        try (var input = Database.class.getResourceAsStream("/database-test.properties")) {
            if (input == null) throw new IOException("Не знайдено тестову конфігурацію");
            settings.load(input);
        }
        return new Database(value(settings, "DB_URL", "db.url"),
                value(settings, "DB_USER", "db.user"), value(settings, "DB_PASSWORD", "db.password"));
    }

    private static String value(Properties settings, String environment, String property) {
        String value = System.getenv(environment);
        return value == null ? settings.getProperty(property) : value;
    }
}
