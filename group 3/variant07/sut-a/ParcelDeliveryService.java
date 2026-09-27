public final class ParcelDeliveryService {
    public record Request(int weightGrams, int distanceKm, boolean express, boolean insured) {}

    public record Result(String status, int billableKilograms, long basePrice, long distanceFee, long optionsFee, long total) {}

    public interface RouteCapacity {
        int availableGrams();
    }

    public interface ExpressPolicy {
        boolean isSupported();
    }

    public interface ShipmentRepository {
        void save(Request request, Result result);
    }

    private final RouteCapacity capacity;
    private final ExpressPolicy express;
    private final ShipmentRepository shipments;

    public ParcelDeliveryService(RouteCapacity capacity, ExpressPolicy express, ShipmentRepository shipments) {
        this.capacity = java.util.Objects.requireNonNull(capacity, "Залежність не задано: capacity");
        this.express = java.util.Objects.requireNonNull(express, "Залежність не задано: express");
        this.shipments = java.util.Objects.requireNonNull(shipments, "Залежність не задано: shipments");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.weightGrams(), 1, 29_999, "Вага посилки");
        checkRange(request.distanceKm(), 1, 3_000, "Відстань");
        int available = capacity.availableGrams();
        if (available < 0) {
            throw new IllegalStateException("Від’ємна доступна вантажність");
        }
        if (available < request.weightGrams()) {
            return rejected("Недостатня вантажність");
        }
        if (request.express() && !express.isSupported()) {
            return rejected("Експрес-доставка недоступна");
        }
        int kilograms = (request.weightGrams() + 999) / 1_000;
        long basePrice = 4_000 + kilograms * 1_000L;
        long distanceFee = request.distanceKm() >= 500 ? 3_000 : 0;
        long options;
        if (request.express() && request.insured()) {
            options = 6_000;
        } else if (request.express()) {
            options = 4_000;
        } else if (request.insured()) {
            options = 2_000;
        } else {
            options = 0;
        }
        Result result = new Result("Прийнято", kilograms, basePrice, distanceFee, options,
                basePrice + distanceFee + options);
        shipments.save(request, result);
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
