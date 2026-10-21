package hotel.flaky.case3;

import hotel.BookingRejectedException;

import java.time.LocalDate;

/**
 * ПОЛАМАНИЙ КОД (кейс 3): залежність від системного годинника й часового поясу JVM.
 * У реальному BookingPolicy дат немає (календар не входить у модель SUT),
 * тому правило "не можна бронювати в минулому" винесено в окремий демо-клас.
 */
public class BadDatePolicy {
    public void validate(LocalDate checkIn) {
        // Бере системну дату в часовому поясі JVM — непостійне значення!
        LocalDate today = LocalDate.now();
        if (checkIn.isBefore(today)) {
            throw new BookingRejectedException("Cannot book in the past");
        }
    }
}
