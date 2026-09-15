public final class EnrollmentService {
    public record Request(int courseCredits, int currentCredits, boolean scholarship, boolean early) {}

    public record Result(String status, int credits, int discountPercent, long total, int remainingSeats) {}

    public interface PrerequisitePolicy {
        boolean isSatisfied();
    }

    public interface CourseCapacity {
        int freeSeats();
    }

    public interface EnrollmentRepository {
        void save(Request request, Result result);
    }

    private final PrerequisitePolicy prerequisites;
    private final CourseCapacity capacity;
    private final EnrollmentRepository enrollments;

    public EnrollmentService(PrerequisitePolicy prerequisites, CourseCapacity capacity, EnrollmentRepository enrollments) {
        this.prerequisites = java.util.Objects.requireNonNull(prerequisites, "Залежність не задано: prerequisites");
        this.capacity = java.util.Objects.requireNonNull(capacity, "Залежність не задано: capacity");
        this.enrollments = java.util.Objects.requireNonNull(enrollments, "Залежність не задано: enrollments");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.courseCredits(), 1, 11, "Кредити курсу");
        checkRange(request.currentCredits(), 0, 30, "Поточне навантаження");
        int credits = request.currentCredits() + request.courseCredits();
        if (credits >= 30) {
            return rejected("Перевищено навантаження");
        }
        if (!prerequisites.isSatisfied()) {
            return rejected("Не виконано передумови");
        }
        int seats = capacity.freeSeats();
        if (seats < 0) {
            throw new IllegalStateException("Від’ємна кількість вільних місць");
        }
        if (seats == 0) {
            return rejected("Немає місць");
        }
        int discount;
        if (request.scholarship() && request.early()) {
            discount = 25;
        } else if (request.scholarship()) {
            discount = 20;
        } else if (request.early()) {
            discount = 10;
        } else {
            discount = 0;
        }
        long base = request.courseCredits() * 50_000L;
        Result result = new Result("Зараховано", credits, discount, base - base * discount / 100, seats - 1);
        enrollments.save(request, result);
        return result;
    }

    private static Result rejected(String status) {
        return new Result(status, 0, 0, 0, 0);
    }

    private static void checkRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Недопустиме значення: " + field);
        }
    }
}
