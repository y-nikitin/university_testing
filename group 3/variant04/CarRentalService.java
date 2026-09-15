public final class CarRentalService {
    public record Request(int age, int days, boolean premium, boolean insured) {}

    public record Result(String status, long rental, long ageSurcharge, long insurance, long deposit, long total) {}

    public interface FleetAvailability {
        boolean isAvailable();
    }

    public interface DriverHistory {
        boolean hasIncident();
    }

    public interface RentalRepository {
        void save(Request request, Result result);
    }

    private final FleetAvailability fleet;
    private final DriverHistory history;
    private final RentalRepository rentals;

    public CarRentalService(FleetAvailability fleet, DriverHistory history, RentalRepository rentals) {
        this.fleet = java.util.Objects.requireNonNull(fleet, "Залежність не задано: fleet");
        this.history = java.util.Objects.requireNonNull(history, "Залежність не задано: history");
        this.rentals = java.util.Objects.requireNonNull(rentals, "Залежність не задано: rentals");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.age(), 21, 74, "Вік водія");
        checkRange(request.days(), 1, 30, "Строк оренди");
        if (!fleet.isAvailable()) {
            return rejected("Немає автомобіля");
        }
        boolean young = request.age() < 25;
        if ((young && request.premium()) && history.hasIncident()) {
            return rejected("Відмова за історією");
        }
        long base = request.days() * (request.premium() ? 90_000L : 50_000L);
        long rental = request.days() > 7 ? base - base * 10 / 100 : base;
        long ageSurcharge = young ? request.days() * 10_000L : 0;
        long insurance = request.insured() ? request.days() * 8_000L : 0;
        long deposit = request.insured() ? 100_000 : 200_000;
        Result result = new Result("Оформлено", rental, ageSurcharge, insurance, deposit,
                rental + ageSurcharge + insurance + deposit);
        rentals.save(request, result);
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
