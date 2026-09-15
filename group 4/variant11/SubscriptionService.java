public final class SubscriptionService {
    public record Request(int users, int months, boolean nonprofitClaimed, boolean autoRenew) {}

    public record Result(String status, long total, long restorationFee, String subscriptionState) {}

    public interface SubscriptionState {
        String currentStatus();
    }

    public interface NonprofitRegistry {
        boolean isEligible();
    }

    public interface BillingRepository {
        void save(Request request, Result result);
    }

    private final SubscriptionState subscriptions;
    private final NonprofitRegistry nonprofits;
    private final BillingRepository billing;

    public SubscriptionService(SubscriptionState subscriptions, NonprofitRegistry nonprofits, BillingRepository billing) {
        this.subscriptions = java.util.Objects.requireNonNull(subscriptions, "Залежність не задано: subscriptions");
        this.nonprofits = java.util.Objects.requireNonNull(nonprofits, "Залежність не задано: nonprofits");
        this.billing = java.util.Objects.requireNonNull(billing, "Залежність не задано: billing");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.users(), 1, 49, "Кількість користувачів");
        checkRange(request.months(), 1, 12, "Строк продовження");
        String state = subscriptions.currentStatus();
        if (!"Активна".equals(state) && !"Прострочена".equals(state) && !"Заблокована".equals(state)) {
            throw new IllegalStateException("Невідомий стан підписки");
        }
        if ("Заблокована".equals(state)) {
            return new Result("Підписку заблоковано", 0, 0, state);
        }
        boolean nonprofit = request.nonprofitClaimed() && nonprofits.isEligible();
        int discount;
        if (nonprofit && request.autoRenew()) {
            discount = 20;
        } else if (nonprofit) {
            discount = 20;
        } else if (request.autoRenew()) {
            discount = 5;
        } else {
            discount = 0;
        }
        long base = request.users() * request.months() * 20_000L;
        long total = base - base * discount / 100;
        if (request.months() >= 11) {
            total = Math.max(0, total - 10_000);
        }
        long restorationFee = "Прострочена".equals(state) ? 5_000 : 0;
        Result result = new Result("Продовжено", total + restorationFee, restorationFee, "Активна");
        billing.save(request, result);
        return result;
    }

    private static void checkRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Недопустиме значення: " + field);
        }
    }
}
