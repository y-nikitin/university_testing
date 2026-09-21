public final class BookingService {
    public record Request(int nights, int guests, boolean loyaltyClaimed, boolean breakfast) {}

    public record Result(String status, int nights, int guests, int discountPercent, long accommodation, long breakfastCost, long total) {}

    public interface RoomAvailability {
        boolean isAvailable();
    }

    public interface LoyaltyProvider {
        boolean isMember();
    }

    public interface BookingRepository {
        void save(Request request, Result result);
    }

    private final RoomAvailability rooms;
    private final LoyaltyProvider loyalty;
    private final BookingRepository bookings;

    public BookingService(RoomAvailability rooms, LoyaltyProvider loyalty, BookingRepository bookings) {
        this.rooms = java.util.Objects.requireNonNull(rooms, "Залежність не задано: rooms");
        this.loyalty = java.util.Objects.requireNonNull(loyalty, "Залежність не задано: loyalty");
        this.bookings = java.util.Objects.requireNonNull(bookings, "Залежність не задано: bookings");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.nights(), 1, 29, "Кількість ночей");
        checkRange(request.guests(), 1, 4, "Кількість гостей");
        if (!rooms.isAvailable()) {
            return rejected("Немає номера");
        }
        boolean member = request.loyaltyClaimed() && loyalty.isMember();
        boolean longStay = request.nights() > 7;
        int discount;
        if (member && longStay) {
            discount = 15;
        } else if (member) {
            discount = 10;
        } else if (longStay) {
            discount = 5;
        } else {
            discount = 0;
        }
        long base = request.nights() * 120_000L;
        long accommodation = base - base * discount / 100;
        long breakfast = request.breakfast() ? request.nights() * Math.min(request.guests(), 3) * 15_000L : 0;
        Result result = new Result("Підтверджено", request.nights(), request.guests(),
                discount, accommodation, breakfast, accommodation + breakfast);
        bookings.save(request, result);
        return result;
    }

    private static Result rejected(String status) {
        return new Result(status, 0, 0, 0, 0, 0, 0);
    }

    private static void checkRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Недопустиме значення: " + field);
        }
    }
}
