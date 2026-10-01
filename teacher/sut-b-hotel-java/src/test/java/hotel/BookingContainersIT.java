package hotel;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class BookingContainersIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Container
    static final GenericContainer<?> wiremock = new GenericContainer<>("wiremock/wiremock:3.13.1")
            .withExposedPorts(8080).waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));
    private static final ObjectMapper json = new ObjectMapper();
    private static final HttpClient http = HttpClient.newHttpClient();
    private Connection connection;
    private Room room;
    private BookingService service;

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static URI endpoint(String path) {
        return URI.create("http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080) + path);
    }

    private static JsonNode admin(String path, Object body) throws Exception {
        var request = HttpRequest.newBuilder(endpoint(path)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, response.body());
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }

    @BeforeAll
    static void migrate() throws Exception {
        try (var c = connect(); var sql = c.createStatement()) {
            sql.execute(Files.readString(Path.of("migrations/V1__create_tables.sql")));
            sql.execute(Files.readString(Path.of("migrations/V2__seed_statuses.sql")));
        }
    }

    @BeforeEach
    void prepare() throws Exception {
        admin("/__admin/reset", Map.of());
        connection = connect();
        try (var sql = connection.createStatement()) {
            sql.execute("TRUNCATE bookings, rooms RESTART IDENTITY CASCADE");
        }
        room = new JdbcRoomRepository(connection).create("101", 2, 120000);
        service = new BookingService(new JdbcRoomRepository(connection), new JdbcBookingRepository(connection),
                new HttpBookingPolicy(endpoint("/"), Duration.ofSeconds(2)));
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) connection.close();
    }

    private void stub(int status, String body) throws Exception {
        admin("/__admin/mappings", Map.of("request", Map.of("method", "POST", "url", "/booking-policy"),
                "response", Map.of("status", status, "headers", Map.of("Content-Type", "application/json"), "body", body)));
    }

    private void verifyRequests(int count, int nights, int guests) throws Exception {
        var requests = admin("/__admin/requests/find", Map.of("method", "POST", "url", "/booking-policy")).get("requests");
        assertEquals(count, requests.size());
        if (count > 0) {
            assertEquals(json.readTree(json.writeValueAsString(Map.of("roomId", room.id(), "nights", nights, "guests", guests))),
                    json.readTree(requests.get(0).get("body").asText()));
        }
    }

    private void emptyDatabase() throws Exception {
        try (var c = connect(); var q = c.createStatement(); var r = q.executeQuery("SELECT count(*) FROM bookings")) {
            assertTrue(r.next());
            assertEquals(0, r.getLong(1));
        }
    }

    @Test
    @DisplayName("HTTP-дозвіл: знижка і статус збережені в PostgreSQL")
    void accepted() throws Exception {
        stub(200, "{\"allowed\":true}");
        var created = service.createBooking(new BookingRequest("B1", room.id(), 7, 2));
        try (var c = connect(); var q = c.prepareStatement("SELECT total,status FROM bookings WHERE id=?")) {
            q.setLong(1, created.id());
            try (var r = q.executeQuery()) {
                assertTrue(r.next());
                assertEquals(798000, r.getLong(1));
                assertEquals("CONFIRMED", r.getString(2));
            }
        }
        verifyRequests(1, 7, 2);
    }

    @Test
    @DisplayName("HTTP-відмова: запит є, бронювання немає")
    void denied() throws Exception {
        stub(200, "{\"allowed\":false}");
        assertThrows(BookingRejectedException.class, () -> service.createBooking(new BookingRequest("B2", room.id(), 2, 2)));
        emptyDatabase();
        verifyRequests(1, 2, 2);
    }

    @Test
    @DisplayName("HTTP 503: помилка без запису в БД")
    void unavailable() throws Exception {
        stub(503, "{}");
        assertThrows(IllegalStateException.class, () -> service.createBooking(new BookingRequest("B3", room.id(), 2, 2)));
        emptyDatabase();
        verifyRequests(1, 2, 2);
    }

    @Test
    @DisplayName("Локальна відмова: немає запису та HTTP-виклику")
    void localRejection() throws Exception {
        assertThrows(BookingRejectedException.class, () -> service.createBooking(new BookingRequest("B4", room.id(), 2, 3)));
        emptyDatabase();
        verifyRequests(0, 2, 3);
    }
}
