package hotel;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;

import java.net.URI;
import java.sql.*;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Testcontainers
class BookingContainersSlidesIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");
    @Container
    static final GenericContainer<?> wiremock = new GenericContainer<>("wiremock/wiremock:3.13.1")
            .withExposedPorts(8080).waitingFor(Wait.forHttp("/__admin/mappings").forStatusCode(200));
    private WireMock wireMock;
    private Connection connection;
    private Room room;
    private BookingService service;

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static URI endpoint(String path) {
        return URI.create("http://" + wiremock.getHost() + ":" + wiremock.getMappedPort(8080) + path);
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                // Готові SQL-файли вже містять BEGIN і COMMIT.
                .executeInTransaction(false)
                .load().migrate();
    }

    @BeforeEach
    void prepare() throws Exception {
        wireMock = new WireMock(wiremock.getHost(), wiremock.getMappedPort(8080));
        wireMock.resetMappings();
        wireMock.resetRequests();
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

    private void verifyRequests(int count, int nights, int guests) {
        wireMock.verify(count, postRequestedFor(urlEqualTo("/booking-policy")));
        if (count > 0) {
            wireMock.verify(count, postRequestedFor(urlEqualTo("/booking-policy"))
                    .withRequestBody(matchingJsonPath("$.roomId", equalTo(Long.toString(room.id()))))
                    .withRequestBody(matchingJsonPath("$.nights", equalTo(Integer.toString(nights))))
                    .withRequestBody(matchingJsonPath("$.guests", equalTo(Integer.toString(guests)))));
        }
    }

    private record BookingRow(long total, String status) {}

    private BookingRow readBooking(Connection connection, long id) throws SQLException {
        try (var q = connection.prepareStatement("SELECT total,status FROM bookings WHERE id=?")) {
            q.setLong(1, id);
            try (var r = q.executeQuery()) {
                assertTrue(r.next());
                return new BookingRow(r.getLong("total"), r.getString("status"));
            }
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
        wireMock.stubFor(post(urlEqualTo("/booking-policy")).willReturn(okJson("{\"allowed\":true}")));
        var created = service.createBooking(new BookingRequest("B1", room.id(), 7, 2));
        try (var verify = connect()) {
            BookingRow row = readBooking(verify, created.id());
            assertEquals("CONFIRMED", row.status());
            assertEquals(798000, row.total());
        }
        verifyRequests(1, 7, 2);
    }

    @Test
    @DisplayName("HTTP-відмова: запит є, бронювання немає")
    void denied() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/booking-policy")).willReturn(okJson("{\"allowed\":false}")));
        assertThrows(BookingRejectedException.class, () -> service.createBooking(new BookingRequest("B2", room.id(), 2, 2)));
        emptyDatabase();
        verifyRequests(1, 2, 2);
    }

    @Test
    @DisplayName("HTTP 503: помилка без запису в БД")
    void unavailable() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/booking-policy")).willReturn(aResponse().withStatus(503)));
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
