package ua.course.courses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.assertEquals;
class EnvironmentTest {
    @Test @DisplayName("Підключення до тестової бази даних")
    void connectsToDatabase() throws Exception {
        try (var connection=Database.connect(); var statement=connection.createStatement(); var result=statement.executeQuery("SELECT 1")) {
            result.next(); assertEquals(1,result.getInt(1));
        }
    }
}
