public final class LoyaltyService {
    public record Request(int purchaseAmount, int pointsToSpend, boolean gold, boolean promotion) {}

    public record Result(String status, long amountDue, int pointsSpent, long pointsEarned, long balance) {}

    public interface MemberStatus {
        boolean isActive();
    }

    public interface PointsBalance {
        long currentBalance();
    }

    public interface LoyaltyRepository {
        void save(Request request, Result result);
    }

    private final MemberStatus members;
    private final PointsBalance points;
    private final LoyaltyRepository operations;

    public LoyaltyService(MemberStatus members, PointsBalance points, LoyaltyRepository operations) {
        this.members = java.util.Objects.requireNonNull(members, "Залежність не задано: members");
        this.points = java.util.Objects.requireNonNull(points, "Залежність не задано: points");
        this.operations = java.util.Objects.requireNonNull(operations, "Залежність не задано: operations");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.purchaseAmount(), 100, 1_000_000, "Сума покупки");
        checkRange(request.pointsToSpend(), 0, 4_999, "Бали для списання");
        if (!members.isActive()) {
            return rejected("Рахунок неактивний");
        }
        long balance = points.currentBalance();
        if (balance < 0) {
            throw new IllegalStateException("Від’ємний баланс балів");
        }
        if (request.pointsToSpend() > balance) {
            return rejected("Недостатньо балів");
        }
        if (request.pointsToSpend() >= request.purchaseAmount() / 200) {
            return rejected("Перевищено частку оплати");
        }
        long amountDue = request.purchaseAmount() - request.pointsToSpend() * 100L;
        int multiplier;
        if (request.gold() && request.promotion()) {
            multiplier = 4;
        } else if (request.gold() || request.promotion()) {
            multiplier = 2;
        } else {
            multiplier = 1;
        }
        long earned = amountDue / 10_000 * multiplier;
        long updatedBalance = Math.addExact(balance - request.pointsToSpend(), earned);
        Result result = new Result("Виконано", amountDue, request.pointsToSpend(), earned, updatedBalance);
        operations.save(request, result);
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
