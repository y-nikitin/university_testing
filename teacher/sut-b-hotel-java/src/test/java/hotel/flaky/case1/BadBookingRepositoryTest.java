package hotel.flaky.case1;

import hotel.*;
import hotel.flaky.FlakyDb;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;

import static hotel.BookingStatus.CANCELLED;
import static hotel.BookingStatus.CONFIRMED;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ПОЛАМАНИЙ КОД (кейс 1): залежність від порядку виконання та від "чужих" даних.
 * testB розраховує, що testA вже створив бронювання з id = 1.
 * Схема спільна для всього класу, тому id справді починаються з 1 — тест проходить,
 * якщо testA виконався першим, і падає з ResourceNotFoundException, якщо першим виконався testB.
 *
 * Запуск (VM options): -Ddemo.broken=true
 * Повторити 50 разів: IntelliJ -> Run Configuration -> Repeat: N Times = 50.
 */
@EnabledIfSystemProperty(named = "demo.broken", matches = "true")
@TestMethodOrder(MethodOrderer.Random.class)
class BadBookingRepositoryTest {
    private static Connection connection;
    private static String schema;
    private static JdbcBookingRepository repository;
    private static BookingService service;
    private static Room room;

    @BeforeAll
    static void createSharedSchema() throws Exception {
        connection = FlakyDb.connect();
        schema = FlakyDb.createSchema(connection, "flaky1_bad");
        var rooms = new JdbcRoomRepository(connection);
        repository = new JdbcBookingRepository(connection);
        service = new BookingService(rooms, repository, (r, nights, guests) -> true);
        room = rooms.create("101", 2, 120_000);
    }

    @AfterAll
    static void dropSchema() throws Exception {
        FlakyDb.dropSchema(connection, schema);
    }

    @Test
    void testA_createBooking() throws Exception {
        // Створює запис і сподівається, що йому призначиться id = 1
        repository.create(new Booking(0, "B1", room.id(), 2, 2, 240_000, CONFIRMED));
    }

    @Test
    void testB_cancelBooking() throws Exception {
        // Падає, якщо виконається першим! Очікує id = 1 від testA
        Booking cancelled = service.cancelBooking(1L);
        assertEquals(CANCELLED, cancelled.status());
    }
}
