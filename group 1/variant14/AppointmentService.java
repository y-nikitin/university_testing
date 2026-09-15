public final class AppointmentService {
    public record Request(int age, int minutes, boolean referral, boolean remote) {}

    public record Result(String status, int minutes, boolean remote, long total) {}

    public interface GuardianConsent {
        boolean isGranted();
    }

    public interface ScheduleAvailability {
        int freeMinutes();
    }

    public interface AppointmentRepository {
        void save(Request request, Result result);
    }

    private final GuardianConsent consent;
    private final ScheduleAvailability schedule;
    private final AppointmentRepository appointments;

    public AppointmentService(GuardianConsent consent, ScheduleAvailability schedule, AppointmentRepository appointments) {
        this.consent = java.util.Objects.requireNonNull(consent, "Залежність не задано: consent");
        this.schedule = java.util.Objects.requireNonNull(schedule, "Залежність не задано: schedule");
        this.appointments = java.util.Objects.requireNonNull(appointments, "Залежність не задано: appointments");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.age(), 0, 120, "Вік");
        checkRange(request.minutes(), 15, 60, "Тривалість");
        if (request.minutes() % 15 != 0) {
            throw new IllegalArgumentException("Тривалість має становити 15, 30, 45 або 60 хвилин");
        }
        if (request.age() <= 18 && !consent.isGranted()) {
            return rejected("Потрібна згода представника");
        }
        if (request.remote() && request.minutes() > 30) {
            return rejected("Недоступний формат");
        }
        int available = schedule.freeMinutes();
        if (available < 0) {
            throw new IllegalStateException("Некоректна тривалість доступного проміжку");
        }
        if (available < request.minutes()) {
            return rejected("Недостатній проміжок");
        }
        int discount;
        if (request.referral() && request.remote()) {
            discount = 20;
        } else if (request.referral()) {
            discount = 15;
        } else if (request.remote()) {
            discount = 10;
        } else {
            discount = 0;
        }
        long base = request.minutes() / 15 * 10_000L;
        long total = base - base * discount / 100;
        if (request.age() > 65) {
            total = Math.max(0, total - 2_000);
        }
        Result result = new Result("Записано", request.minutes(), request.remote(), total);
        appointments.save(request, result);
        return result;
    }

    private static Result rejected(String status) {
        return new Result(status, 0, false, 0);
    }

    private static void checkRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Недопустиме значення: " + field);
        }
    }
}
