public final class ReservationService {
    public record Request(int guests, int hoursBefore, boolean terrace, boolean celebration) {}

    public record Result(String status, int guests, String zone, long deposit) {}

    public interface TerraceAvailability {
        boolean isOpen();
    }

    public interface SeatAvailability {
        int freeSeats();
    }

    public interface ReservationRepository {
        void save(Request request, Result result);
    }

    private final TerraceAvailability terrace;
    private final SeatAvailability seats;
    private final ReservationRepository reservations;

    public ReservationService(TerraceAvailability terrace, SeatAvailability seats, ReservationRepository reservations) {
        this.terrace = java.util.Objects.requireNonNull(terrace, "Залежність не задано: terrace");
        this.seats = java.util.Objects.requireNonNull(seats, "Залежність не задано: seats");
        this.reservations = java.util.Objects.requireNonNull(reservations, "Залежність не задано: reservations");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.guests(), 1, 11, "Кількість гостей");
        checkRange(request.hoursBefore(), 2, 720, "Години до візиту");
        if (request.terrace() && !terrace.isOpen()) {
            return rejected("Тераса недоступна");
        }
        int available = seats.freeSeats();
        if (available < 0) {
            throw new IllegalStateException("Від’ємна кількість вільних місць");
        }
        if (available < request.guests()) {
            return rejected("Недостатньо місць");
        }
        long base = request.guests() >= 6 ? request.guests() * 10_000L : 0;
        long options;
        if (request.terrace() && request.celebration()) {
            options = 13_000;
        } else if (request.terrace()) {
            options = 5_000;
        } else if (request.celebration()) {
            options = 8_000;
        } else {
            options = 0;
        }
        long urgent = request.hoursBefore() <= 24 ? 3_000 : 0;
        Result result = new Result("Підтверджено", request.guests(),
                request.terrace() ? "Тераса" : "Зал", base + options + urgent);
        reservations.save(request, result);
        return result;
    }

    private static Result rejected(String status) {
        return new Result(status, 0, "", 0);
    }

    private static void checkRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Недопустиме значення: " + field);
        }
    }
}
