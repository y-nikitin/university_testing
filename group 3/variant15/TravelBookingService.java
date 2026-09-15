public final class TravelBookingService {
    public record Request(int travelers, int daysBefore, boolean premium, boolean flexible) {}

    public record Result(String status, int travelers, int discountPercent, long options, long lateFee, long total) {}

    public interface RoutePolicy {
        boolean isOpen();
    }

    public interface TravelCapacity {
        int freeSeats();
    }

    public interface TravelRepository {
        void save(Request request, Result result);
    }

    private final RoutePolicy route;
    private final TravelCapacity capacity;
    private final TravelRepository bookings;

    public TravelBookingService(RoutePolicy route, TravelCapacity capacity, TravelRepository bookings) {
        this.route = java.util.Objects.requireNonNull(route, "Залежність не задано: route");
        this.capacity = java.util.Objects.requireNonNull(capacity, "Залежність не задано: capacity");
        this.bookings = java.util.Objects.requireNonNull(bookings, "Залежність не задано: bookings");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.travelers(), 1, 6, "Кількість туристів");
        checkRange(request.daysBefore(), 1, 179, "Дні до виїзду");
        if (!route.isOpen()) {
            return rejected("Напрямок закритий");
        }
        int seats = capacity.freeSeats();
        if (seats < 0) {
            throw new IllegalStateException("Від’ємна кількість вільних місць");
        }
        if (seats < request.travelers()) {
            return rejected("Недостатньо місць");
        }
        long optionRate;
        if (request.premium() && request.flexible()) {
            optionRate = 45_000;
        } else if (request.premium()) {
            optionRate = 25_000;
        } else if (request.flexible()) {
            optionRate = 20_000;
        } else {
            optionRate = 0;
        }
        long base = request.travelers() * 100_000L;
        int discount = request.daysBefore() > 60 ? 10 : 0;
        long options = request.travelers() * optionRate;
        long lateFee = request.daysBefore() < 7 ? request.travelers() * 15_000L : 0;
        long total = base - base * discount / 100 + options + lateFee;
        Result result = new Result("Заброньовано", request.travelers(), discount, options, lateFee, total);
        bookings.save(request, result);
        return result;
    }

    private static Result rejected(String status) {
        return new Result(status, 0, 0, 0, 0, 0);
    }

    private static void checkRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Недопустиме значення: " + field);
        }
    }
}
