package hotel.flaky.case2;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ПОЛАМАНИЙ КОД (кейс 2): Thread.sleep замість очікування умови.
 *
 * Швидка "локальна" машина:   -Ddemo.broken=true -Ddemo.delayMs=100   -> тест зелений
 * Повільний "CI" (типово):    -Ddemo.broken=true -Ddemo.delayMs=1200  -> AssertionError: expected CONFIRMED but was PENDING
 */
@EnabledIfSystemProperty(named = "demo.broken", matches = "true")
class AsyncStatusBadTest {
    private AsyncBookingProcessor bookingApi;

    @BeforeEach
    void start() {
        bookingApi = new AsyncBookingProcessor(Long.getLong("demo.delayMs", 1200));
    }

    @AfterEach
    void stop() {
        bookingApi.close();
    }

    @Test
    void testAsyncBookingStatusBad() throws InterruptedException {
        long bookingId = bookingApi.initiateBooking(101L, "User1");

        // Погано! Фіксоване очікування 1 секунда
        Thread.sleep(1000);

        assertEquals(AsyncBookingProcessor.Status.CONFIRMED, bookingApi.getBookingStatus(bookingId));
    }
}
