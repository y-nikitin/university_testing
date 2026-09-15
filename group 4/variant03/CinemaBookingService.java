public final class CinemaBookingService {
    public record Request(int tickets, int age, boolean evening, boolean studentClaimed) {}

    public record Result(String status, int tickets, long total) {}

    public interface SeatAvailability {
        int freeSeats();
    }

    public interface StudentRegistry {
        boolean isEligible();
    }

    public interface TicketRepository {
        void save(Request request, Result result);
    }

    private final SeatAvailability seats;
    private final StudentRegistry students;
    private final TicketRepository tickets;

    public CinemaBookingService(SeatAvailability seats, StudentRegistry students, TicketRepository tickets) {
        this.seats = java.util.Objects.requireNonNull(seats, "Залежність не задано: seats");
        this.students = java.util.Objects.requireNonNull(students, "Залежність не задано: students");
        this.tickets = java.util.Objects.requireNonNull(tickets, "Залежність не задано: tickets");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.tickets(), 1, 7, "Кількість квитків");
        checkRange(request.age(), 0, 120, "Вік");
        if (request.age() < 11 && request.evening()) {
            return rejected("Вікове обмеження");
        }
        int available = seats.freeSeats();
        if (available < 0) {
            throw new IllegalStateException("Некоректні відомості про вільні місця");
        }
        if (available < request.tickets()) {
            return rejected("Недостатньо місць");
        }
        int discount;
        if (request.age() < 12) {
            discount = 50;
        } else if (request.studentClaimed() && students.isEligible()) {
            discount = 20;
        } else {
            discount = 0;
        }
        long base = request.tickets() * (request.evening() ? 15_000L : 10_000L);
        long total = base - base * discount / 100;
        if (request.tickets() > 5) {
            total -= 2_000;
        }
        Result result = new Result("Заброньовано", request.tickets(), total);
        tickets.save(request, result);
        return result;
    }

    private static Result rejected(String status) {
        return new Result(status, 0, 0);
    }

    private static void checkRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Недопустиме значення: " + field);
        }
    }
}
