package hotel.flaky.case4;

import hotel.*;
import hotel.flaky.FlakyDb;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ПОЛАМАНИЙ КОД (кейс 4): спільний жорстко прописаний номер кімнати + спільна БД + паралельний запуск.
 * 20 повторень виконуються одночасно й усі вставляють кімнату "101" у таблицю rooms (UNIQUE number).
 * Послідовно тест зелений; паралельно — DuplicateKeyException (підтип DataIntegrityViolationException).
 *
 *
 * VM options для запуску у 8 потоків:
 *   -Ddemo.broken=true
 *   -Djunit.jupiter.execution.parallel.enabled=true
 *   -Djunit.jupiter.execution.parallel.config.strategy=fixed
 *   -Djunit.jupiter.execution.parallel.config.fixed.parallelism=8
 * Без прапорців parallel.enabled тест виконується послідовно і проходить.
 */
@EnabledIfSystemProperty(named = "demo.broken", matches = "true")
@Execution(ExecutionMode.CONCURRENT)
class RoomRaceBadIT {
    private static final String ROOM_NUMBER = "101"; // Конфлікт у паралельних потоках!

    @RepeatedTest(20)
    void testBookingProcess() throws Exception {
        try (Connection connection = FlakyDb.connect()) {
            var rooms = new JdbcRoomRepository(connection);
            var bookings = new JdbcBookingRepository(connection);
            var service = new BookingService(rooms, bookings, (r, nights, guests) -> true);
            Room room = null;
            Booking booking = null;
            try {
                room = rooms.create(ROOM_NUMBER, 2, 120_000);
                booking = service.createBooking(
                        new BookingRequest("G-" + UUID.randomUUID().toString().substring(0, 8), room.id(), 2, 2));
                assertTrue(booking.id() > 0);
            } finally {
                // Прибираємо лише те, що створили самі: спочатку бронювання, потім номер
                if (booking != null) bookings.delete(booking.id());
                if (room != null) rooms.delete(room.id());
            }
        }
    }
}
