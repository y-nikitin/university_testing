public final class InsuranceService {
    public record Request(int age, int insuredAmount, boolean smoker, boolean hazardousActivity) {}

    public record Result(String status, long insuredAmount, long basePremium, long ageSurcharge, long riskSurcharge, int discountPercent, long total) {}

    public interface UnderwritingPolicy {
        boolean isAllowed();
    }

    public interface ClaimHistory {
        boolean hasNoClaimsDiscount();
    }

    public interface QuoteRepository {
        void save(Request request, Result result);
    }

    private final UnderwritingPolicy policy;
    private final ClaimHistory history;
    private final QuoteRepository quotes;

    public InsuranceService(UnderwritingPolicy policy, ClaimHistory history, QuoteRepository quotes) {
        this.policy = java.util.Objects.requireNonNull(policy, "Залежність не задано: policy");
        this.history = java.util.Objects.requireNonNull(history, "Залежність не задано: history");
        this.quotes = java.util.Objects.requireNonNull(quotes, "Залежність не задано: quotes");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.age(), 18, 74, "Вік");
        checkRange(request.insuredAmount(), 100_000, 10_000_000, "Страхова сума");
        if (!policy.isAllowed()) {
            return rejected("Страхування недоступне");
        }
        long base = request.insuredAmount() * 2L / 100;
        long ageFee = request.age() < 25 ? 5_000 : request.age() > 65 ? 8_000 : 0;
        long risk;
        if (request.smoker() && request.hazardousActivity()) {
            risk = 10_000;
        } else if (request.smoker()) {
            risk = 4_000;
        } else if (request.hazardousActivity()) {
            risk = 6_000;
        } else {
            risk = 0;
        }
        long subtotal = base + ageFee + risk;
        int discount = history.hasNoClaimsDiscount() ? 10 : 0;
        long total = Math.max(3_000, subtotal - subtotal * discount / 100);
        Result result = new Result("Пропозицію сформовано", request.insuredAmount(), base,
                ageFee, risk, discount, total);
        quotes.save(request, result);
        return result;
    }

    private static Result rejected(String status) {
        return new Result(status, 0, 0, 0, 0, 0, 0);
    }

    private static void checkRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Недопустиме значення: " + field);
        }
    }
}
