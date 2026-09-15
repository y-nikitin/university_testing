public final class MembershipService {
    public record Request(int months, int visits, boolean offPeak, boolean corporateClaimed) {}

    public record Result(String status, int months, int visits, int discountPercent, long entryFee, long total) {}

    public interface ClubCapacity {
        boolean acceptsMembers();
    }

    public interface CorporateRegistry {
        boolean isEligible();
    }

    public interface MembershipRepository {
        void save(Request request, Result result);
    }

    private final ClubCapacity capacity;
    private final CorporateRegistry corporate;
    private final MembershipRepository memberships;

    public MembershipService(ClubCapacity capacity, CorporateRegistry corporate, MembershipRepository memberships) {
        this.capacity = java.util.Objects.requireNonNull(capacity, "Залежність не задано: capacity");
        this.corporate = java.util.Objects.requireNonNull(corporate, "Залежність не задано: corporate");
        this.memberships = java.util.Objects.requireNonNull(memberships, "Залежність не задано: memberships");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.months(), 1, 12, "Строк абонемента");
        checkRange(request.visits(), 1, 29, "Кількість відвідувань");
        if (!capacity.acceptsMembers()) {
            return rejected("Немає доступних абонементів");
        }
        boolean eligible = request.corporateClaimed() && corporate.isEligible();
        int discount;
        if (request.offPeak() && eligible) {
            discount = 20;
        } else if (eligible) {
            discount = 15;
        } else if (request.offPeak()) {
            discount = 10;
        } else {
            discount = 0;
        }
        long monthly = 200_000 + Math.max(0, request.visits() - 12) * 5_000L;
        long base = request.months() * monthly;
        long entryFee = request.months() >= 11 ? 0 : 10_000;
        long total = base - base * discount / 100 + entryFee;
        Result result = new Result("Активовано", request.months(), request.visits(), discount, entryFee, total);
        memberships.save(request, result);
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
