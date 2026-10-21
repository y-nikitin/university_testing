package hotel.flaky.case3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * ПОЛАМАНИЙ ТЕСТ (кейс 3): "заїзд сьогодні (за UTC) має бути дозволений".
 * Політика ж бере "сьогодні" в часовому поясі JVM, тож результат залежить від поясу й години доби.
 *
 * Запуск (VM options):
 *   -Ddemo.broken=true -Duser.timezone=Pacific/Pago_Pago     (UTC-11)  -> завжди зелений
 *   -Ddemo.broken=true -Duser.timezone=Pacific/Kiritimati    (UTC+14)  -> червоний приблизно з 10:00 до 24:00 UTC
 *   -Ddemo.broken=true -Duser.timezone=Pacific/Kiritimati                              -> зелений
 * Гарантоване падіння в БУДЬ-ЯКУ годину (різниця зон 25 год, дати завжди різні):
 *   -Ddemo.broken=true -Ddemo.clientZone=Pacific/Pago_Pago -Duser.timezone=Pacific/Kiritimati
 * Саме тому на CI (інша година, інший пояс) тест "раптом" падає.
 */
@EnabledIfSystemProperty(named = "demo.broken", matches = "true")
class DatePolicyBadTest {

    @Test
    void shouldAllowBookingForTodayUtc() {
        // Зона клієнта береться з -Ddemo.clientZone (типово UTC)
        LocalDate todayUtc = LocalDate.now(ZoneId.of(System.getProperty("demo.clientZone", "UTC")));

        assertDoesNotThrow(() -> new BadDatePolicy().validate(todayUtc));
    }
}
