public final class ParkingService {
    public record Request(int minutes, int entryHour, String zone, boolean electric, boolean subscriptionClaimed) {}

    public record Result(String status, int billableHours, long total) {}

    public interface ParkingAvailability {
        boolean isAvailable();
    }

    public interface SubscriptionRegistry {
        boolean isValid();
    }

    public interface ParkingRepository {
        void save(Request request, Result result);
    }

    private final ParkingAvailability parking;
    private final SubscriptionRegistry subscriptions;
    private final ParkingRepository receipts;

    public ParkingService(ParkingAvailability parking, SubscriptionRegistry subscriptions, ParkingRepository receipts) {
        this.parking = java.util.Objects.requireNonNull(parking, "Залежність не задано: parking");
        this.subscriptions = java.util.Objects.requireNonNull(subscriptions, "Залежність не задано: subscriptions");
        this.receipts = java.util.Objects.requireNonNull(receipts, "Залежність не задано: receipts");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.minutes(), 1, 1_439, "Тривалість");
        checkRange(request.entryHour(), 0, 23, "Година в’їзду");
        if (!"Центральна".equals(request.zone()) && !"Зовнішня".equals(request.zone())) {
            throw new IllegalArgumentException("Невідома зона");
        }
        if (!parking.isAvailable()) {
            return rejected("Оформлення недоступне");
        }
        if (request.minutes() <= 15) {
            Result result = new Result("Розраховано", 0, 0);
            receipts.save(request, result);
            return result;
        }
        int hours = (request.minutes() - 15 + 59) / 60;
        boolean central = "Центральна".equals(request.zone());
        long base = Math.min(hours * (central ? 5_000L : 3_000L), central ? 30_000 : 20_000);
        boolean subscriber = request.subscriptionClaimed() && subscriptions.isValid();
        int discount;
        if (request.electric() && subscriber) {
            discount = 35;
        } else if (subscriber) {
            discount = 30;
        } else if (request.electric()) {
            discount = 10;
        } else {
            discount = 0;
        }
        long total = base - base * discount / 100;
        if (request.entryHour() >= 22 || request.entryHour() < 5) {
            total = Math.max(0, total - 1_000);
        }
        Result result = new Result("Розраховано", hours, total);
        receipts.save(request, result);
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
