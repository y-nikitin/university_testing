package hotel;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "BOOKING_POLICY_URL="
)
class BookingApiLocalIT {
    @LocalServerPort
    int port;

    @Test
    @DisplayName("Створення номера повертає його дані та адресу")
    void createRoom() {
        String number = UUID.randomUUID().toString().substring(0, 8);
        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "number", number,
                        "capacity", 2,
                        "nightlyPrice", 120000
                ))
                .post("/rooms")
                .then()
                .statusCode(201)
                .header("Location", matchesPattern("/rooms/[0-9]+"))
                .body("id", greaterThan(0))
                .body("number", equalTo(number))
                .body("capacity", equalTo(2))
                .body("nightlyPrice", equalTo(120000));
    }

    @Test
    @DisplayName("Читання створеного номера")
    void readRoom() {
        long roomId = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "number", UUID.randomUUID().toString().substring(0, 8),
                        "capacity", 2,
                        "nightlyPrice", 120000
                ))
                .post("/rooms")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .get("/rooms/{id}", roomId)
                .then()
                .statusCode(200)
                .body("id", equalTo((int) roomId))
                .body("capacity", equalTo(2))
                .body("nightlyPrice", equalTo(120000));
    }

    @Test
    @DisplayName("Створення бронювання без знижки")
    void createBooking() {
        long roomId = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "number", UUID.randomUUID().toString().substring(0, 8),
                        "capacity", 2,
                        "nightlyPrice", 120000
                ))
                .post("/rooms")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");
        Map<String, Object> request = Map.of(
                "reference", UUID.randomUUID().toString(),
                "roomId", roomId,
                "nights", 2,
                "guests", 2
        );
        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(request)
                .post("/bookings")
                .then()
                .statusCode(201)
                .header("Location", matchesPattern("/bookings/[0-9]+"))
                .body("id", greaterThan(0))
                .body("reference", equalTo(request.get("reference")))
                .body("status", equalTo("CONFIRMED"))
                .body("total", equalTo(240000));
    }

    @Test
    @DisplayName("Читання збереженого бронювання")
    void readBooking() {
        long roomId = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "number", UUID.randomUUID().toString().substring(0, 8),
                        "capacity", 2,
                        "nightlyPrice", 120000
                ))
                .post("/rooms")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        long id = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "reference", UUID.randomUUID().toString(),
                        "roomId", roomId,
                        "nights", 2,
                        "guests", 2
                ))
                .post("/bookings")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .get("/bookings/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo((int) id))
                .body("roomId", equalTo((int) roomId))
                .body("nights", equalTo(2))
                .body("guests", equalTo(2))
                .body("total", equalTo(240000))
                .body("status", equalTo("CONFIRMED"));
    }

    @Test
    @DisplayName("Неіснуюче бронювання повертає контракт помилки")
    void missing() {
        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .get("/bookings/{id}", Long.MAX_VALUE)
                .then()
                .statusCode(404)
                .body("code", equalTo("NOT_FOUND"))
                .body("message", not(emptyOrNullString()));
    }

    @Test
    @DisplayName("Повторне скасування відхиляється без зміни ресурсу")
    void cancelConflict() {
        long roomId = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "number", UUID.randomUUID().toString().substring(0, 8),
                        "capacity", 2,
                        "nightlyPrice", 120000
                ))
                .post("/rooms")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        long id = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "reference", UUID.randomUUID().toString(),
                        "roomId", roomId,
                        "nights", 2,
                        "guests", 2
                ))
                .post("/bookings")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .post("/bookings/{id}/cancel", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("total", equalTo(240000));
        Map<String, Object> before = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .get("/bookings/{id}", id)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .post("/bookings/{id}/cancel", id)
                .then()
                .statusCode(409)
                .body("code", equalTo("BOOKING_CONFLICT"))
                .body("message", not(emptyOrNullString()));
        Map<String, Object> after = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .get("/bookings/{id}", id)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("$");
        Assertions.assertEquals(before, after);
    }

    @Test
    @DisplayName("Повний цикл бронювання зі знижкою")
    void workflow() {
        long roomId = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "number", UUID.randomUUID().toString().substring(0, 8),
                        "capacity", 2,
                        "nightlyPrice", 120000
                ))
                .post("/rooms")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        long id = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "reference", UUID.randomUUID().toString(),
                        "roomId", roomId,
                        "nights", 7,
                        "guests", 2
                ))
                .post("/bookings")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .get("/bookings/{id}", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"))
                .body("total", equalTo(798000));
        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .post("/bookings/{id}/cancel", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));
        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .get("/bookings/{id}", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"))
                .body("total", equalTo(798000));
    }

    @ParameterizedTest(name = "{0} ночей: сума {1} копійок")
    @CsvSource({"6,720000", "7,798000", "8,912000"})
    void discount(int nights, int expectedTotal) {
        long roomId = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "number", UUID.randomUUID().toString().substring(0, 8),
                        "capacity", 2,
                        "nightlyPrice", 120000
                ))
                .post("/rooms")
                .then()
                .statusCode(201)
                .extract()
                .jsonPath()
                .getLong("id");

        long id = given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .body(Map.of(
                        "reference", UUID.randomUUID().toString(),
                        "roomId", roomId,
                        "nights", nights,
                        "guests", 2
                ))
                .post("/bookings")
                .then()
                .statusCode(201)
                .body("total", equalTo(expectedTotal))
                .extract()
                .jsonPath()
                .getLong("id");

        given()
                .baseUri("http://127.0.0.1")
                .port(port)
                .contentType("application/json")
                .get("/bookings/{id}", id)
                .then()
                .statusCode(200)
                .body("total", equalTo(expectedTotal));
    }
}
