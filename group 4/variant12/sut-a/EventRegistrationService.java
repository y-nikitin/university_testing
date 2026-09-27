public final class EventRegistrationService {
    public record Request(int participants, int daysBefore, boolean studentClaimed, boolean allowWaitlist) {}

    public record Result(String status, int participants, long total) {}

    public interface EventCapacity {
        int freeSeats();
    }

    public interface StudentRegistry {
        boolean isEligible();
    }

    public interface RegistrationRepository {
        void save(Request request, Result result);
    }

    private final EventCapacity capacity;
    private final StudentRegistry students;
    private final RegistrationRepository registrations;

    public EventRegistrationService(EventCapacity capacity, StudentRegistry students, RegistrationRepository registrations) {
        this.capacity = java.util.Objects.requireNonNull(capacity, "Залежність не задано: capacity");
        this.students = java.util.Objects.requireNonNull(students, "Залежність не задано: students");
        this.registrations = java.util.Objects.requireNonNull(registrations, "Залежність не задано: registrations");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.participants(), 1, 6, "Кількість учасників");
        checkRange(request.daysBefore(), 0, 179, "Дні до події");
        if (request.daysBefore() == 0) {
            return rejected("Реєстрацію закрито");
        }
        int seats = capacity.freeSeats();
        if (seats < 0) {
            throw new IllegalStateException("Від’ємна кількість вільних місць");
        }
        if (seats < request.participants()) {
            if (!request.allowWaitlist()) {
                return rejected("Немає місць");
            }
            Result result = new Result("У списку очікування", request.participants(), 0);
            registrations.save(request, result);
            return result;
        }
        boolean student = request.studentClaimed() && students.isEligible();
        long base = request.participants() * 30_000L;
        int discount = student ? 20 : 0;
        long total = base - base * discount / 100;
        if (request.daysBefore() > 30) {
            total -= 2_000;
        }
        Result result = new Result("Зареєстровано", request.participants(), total);
        registrations.save(request, result);
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
