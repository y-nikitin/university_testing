package hotel.flaky.case3;

import hotel.BookingRejectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ВИПРАВЛЕНИЙ ТЕСТ (кейс 3): зафіксований момент часу й явний часовий пояс.
 * Результат не залежить ні від години запуску, ні від -Duser.timezone.
 * Перевірка: запустіть з -Duser.timezone=Pacific/Pago_Pago, Pacific/Kiritimati, UTC — завжди зелений.
 */
class DatePolicyFixedTest {
    private static final Instant NOW = Instant.parse("2026-10-14T23:30:00Z");

    private static DatePolicy policyIn(String zone) {
        return new DatePolicy(Clock.fixed(NOW, ZoneId.of(zone)));
    }

    @Test
    @DisplayName("Сьогоднішня дата (UTC) дозволена, вчорашня — відхилена")
    void shouldValidateBookingDatesFixed() {
        DatePolicy policy = policyIn("UTC");

        assertDoesNotThrow(() -> policy.validate(LocalDate.parse("2026-10-14")));
        assertDoesNotThrow(() -> policy.validate(LocalDate.parse("2026-10-15")));
        assertThrows(BookingRejectedException.class, () -> policy.validate(LocalDate.parse("2026-10-13")));
    }

    @Test
    @DisplayName("Межа доби: той самий момент у Кірітіматі вже 15 жовтня, тож 14-те — минуле")
    void sameInstantDifferentZoneChangesTheDate() {
        DatePolicy policy = policyIn("Pacific/Kiritimati"); // UTC+14

        assertThrows(BookingRejectedException.class, () -> policy.validate(LocalDate.parse("2026-10-14")));
        assertDoesNotThrow(() -> policy.validate(LocalDate.parse("2026-10-15")));
    }
}
