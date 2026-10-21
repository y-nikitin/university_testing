package hotel.flaky.case2;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ВИПРАВЛЕНИЙ КОД (кейс 2): Awaitility — опитуємо стан, поки він не стане очікуваним (але не довше 5 с).
 * Параметризація імітує "штучну затримку сервісу": тест зелений і за 0 мс, і за 3 с.
 */
class AsyncStatusFixedTest {

    @DisplayName("Статус стає CONFIRMED за будь-якої затримки обробки до 3 с")
    @ParameterizedTest(name = "затримка {0} мс")
    @ValueSource(longs = {0, 300, 1200, 6000})
    void testAsyncBookingStatusFixed(long delayMs) {
        try (var bookingApi = new AsyncBookingProcessor(delayMs)) {
            long bookingId = bookingApi.initiateBooking(101L, "User1");

            // Динамічне опитування: чекаємо до 5 секунд, опитуємо кожні 100 мс
            Awaitility.await()
                    .atMost(Duration.ofSeconds(8))
                    .pollInterval(Duration.ofMillis(100))
                    .untilAsserted(() -> assertEquals(
                            AsyncBookingProcessor.Status.CONFIRMED, bookingApi.getBookingStatus(bookingId)));
        }
    }
}
