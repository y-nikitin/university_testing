package hotel.flaky.case4;

import hotel.*;
import hotel.flaky.FlakyDb;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ВИПРАВЛЕНИЙ КОД (кейс 4): динамічно згенеровані унікальні дані.
 * Те саме навантаження (20 повторень одночасно), але кожен потік має власний номер кімнати.
 *
 * Важливо: rooms.number — VARCHAR(10), тому префікс "ROOM_" + 8 символів (13) не влізе в БД.
 * Беремо 8 символів UUID без префікса (так само робить BookingApiIT).
 *
 * VM options (8 потоків):
 *   -Djunit.jupiter.execution.parallel.enabled=true
 *   -Djunit.jupiter.execution.parallel.config.strategy=fixed
 *   -Djunit.jupiter.execution.parallel.config.fixed.parallelism=8
 * Далі в IntelliJ: Repeat: N Times = 20 -> "8 потоків 20 разів підряд".
 */
@Execution(ExecutionMode.CONCURRENT)
class RoomRaceFixedIT {

    private static String uniqueRoomNumber() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @RepeatedTest(20)
    void testBookingProcess() throws Exception {
        try (Connection connection = FlakyDb.connect()) {
            var rooms = new JdbcRoomRepository(connection);
            var bookings = new JdbcBookingRepository(connection);
            var service = new BookingService(rooms, bookings, (r, nights, guests) -> true);
            Room room = null;
            Booking booking = null;
            try {
                // Кожен потік працює з власним унікальним номером кімнати та кодом бронювання
                room = rooms.create(uniqueRoomNumber(), 2, 120_000);
                booking = service.createBooking(
                        new BookingRequest("G-" + UUID.randomUUID(), room.id(), 2, 2));
                assertTrue(booking.id() > 0);
            } finally {
                if (booking != null) bookings.delete(booking.id());
                if (room != null) rooms.delete(room.id());
            }
        }
    }
}
