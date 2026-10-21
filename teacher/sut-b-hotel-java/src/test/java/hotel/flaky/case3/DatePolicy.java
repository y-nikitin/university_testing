package hotel.flaky.case3;

import hotel.BookingRejectedException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/** ВИПРАВЛЕНИЙ КОД (кейс 3): Clock передається ззовні, тому тест контролює "поточний" момент. */
public class DatePolicy {
    private final Clock clock;

    public DatePolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    public void validate(LocalDate checkIn) {
        LocalDate today = LocalDate.now(clock);
        if (checkIn.isBefore(today)) {
            throw new BookingRejectedException("Cannot book in the past");
        }
    }
}
