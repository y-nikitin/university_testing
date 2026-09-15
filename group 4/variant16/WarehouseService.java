public final class WarehouseService {
    public record Request(int quantity, int reserved, boolean urgent, boolean allowPartial) {}

    public record Result(String status, int requested, int issued, long stock, int reserved, long fee) {}

    public interface ReleasePolicy {
        boolean isAllowed();
    }

    public interface StockProvider {
        long physicalStock();
    }

    public interface WarehouseRepository {
        void save(Request request, Result result);
    }

    private final ReleasePolicy policy;
    private final StockProvider stock;
    private final WarehouseRepository orders;

    public WarehouseService(ReleasePolicy policy, StockProvider stock, WarehouseRepository orders) {
        this.policy = java.util.Objects.requireNonNull(policy, "Залежність не задано: policy");
        this.stock = java.util.Objects.requireNonNull(stock, "Залежність не задано: stock");
        this.orders = java.util.Objects.requireNonNull(orders, "Залежність не задано: orders");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.quantity(), 1, 99, "Запитана кількість");
        checkRange(request.reserved(), 0, 100, "Попередні резерви");
        if (!policy.isAllowed()) {
            return rejected("Видачу заблоковано");
        }
        long physical = stock.physicalStock();
        if (physical < 0) {
            throw new IllegalStateException("Від’ємний фізичний залишок");
        }
        if (request.reserved() > physical) {
            throw new IllegalStateException("Резерви перевищують фізичний залишок");
        }
        long available = physical - request.reserved();
        if (available == 0) {
            return rejected("Немає доступного товару");
        }
        if (available < request.quantity() && !request.allowPartial()) {
            return rejected("Недостатньо товару");
        }
        int issued = (int) Math.min(available, request.quantity());
        boolean partial = issued < request.quantity();
        long surcharge;
        if (request.urgent() && partial) {
            surcharge = 5_000;
        } else if (request.urgent()) {
            surcharge = 5_000;
        } else if (partial) {
            surcharge = 1_000;
        } else {
            surcharge = 0;
        }
        long remaining = physical - issued - (partial ? request.reserved() : 0);
        Result result = new Result(partial ? "Виконано частково" : "Виконано повністю",
                request.quantity(), issued, remaining, request.reserved(), issued * 500L + surcharge);
        orders.save(request, result);
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
