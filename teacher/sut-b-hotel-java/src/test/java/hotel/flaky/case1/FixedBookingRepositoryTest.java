package hotel.flaky.case1;

import hotel.*;
import hotel.flaky.FlakyDb;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.util.UUID;

import static hotel.BookingStatus.CANCELLED;
import static hotel.BookingStatus.CONFIRMED;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ВИПРАВЛЕНИЙ КОД (кейс 1): повна ізоляція даних.
 * Перед кожним тестом бронювання очищаються,
 * кожен тест сам створює свої дані й не покладається на конкретні id.
 * Порядок методів навмисно випадковий: тест має проходити за будь-якого порядку.
 *
 * Запуск: звичайний (без додаткових прапорців). Повторити 50 разів: Repeat: N Times = 50.
 * Для випадкового порядку КЛАСІВ у Maven: mvn test -Dsurefire.runOrder=random
 */
@TestMethodOrder(MethodOrderer.Random.class)
class FixedBookingRepositoryTest {
    private static Connection connection;
    private static String schema;
    private static JdbcBookingRepository repository;
    private static BookingService service;
    private static Room room;

    @BeforeAll
    static void createSchema() throws Exception {
        connection = FlakyDb.connect();
        schema = FlakyDb.createSchema(connection, "flaky1_fixed");
        var rooms = new JdbcRoomRepository(connection);
        repository = new JdbcBookingRepository(connection);
        service = new BookingService(rooms, repository, (r, nights, guests) -> true);
        room = rooms.create("101", 2, 120_000);
    }

    @AfterAll
    static void dropSchema() throws Exception {
        FlakyDb.dropSchema(connection, schema);
    }

    @BeforeEach
    void cleanBookings() throws Exception {
        // Гарантуємо чистий стан (кімната лишається, статуси-довідник не чіпаємо)
        try (var sql = connection.createStatement()) {
            sql.execute("DELETE FROM bookings");
        }
    }

    private Booking newBooking(String prefix) {
        return new Booking(0, prefix + "-" + UUID.randomUUID().toString().substring(0, 8),
                room.id(), 2, 2, 240_000, CONFIRMED);
    }

    @Test
    void testCreateBooking() throws Exception {
        long id = repository.create(newBooking("C"));

        assertTrue(id > 0);
        assertNotNull(repository.findById(id));
    }

    @Test
    void testCancelBooking() throws Exception {
        // Тест сам створює свої дані і користується id, який повернула БД
        long id = repository.create(newBooking("X"));

        Booking cancelled = service.cancelBooking(id);

        assertEquals(CANCELLED, cancelled.status());
        assertEquals(CANCELLED, repository.findById(id).status());
    }
}
