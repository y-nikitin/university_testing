package hotel;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentTest {
    @Test
    void databaseConnectionIsAvailable() throws Exception {
        try (var connection = Database.fromEnvironment().connect()) {
            assertTrue(connection.isValid(2), "Тестова БД має бути доступною");
        }
    }
}
