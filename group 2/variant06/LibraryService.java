public final class LibraryService {
    public record Request(int days, int currentLoans, boolean referenceBook, boolean renewal) {}

    public record Result(String status, int days, long fee, int loanCount) {}

    public interface CatalogAvailability {
        int availableCopies();
    }

    public interface HoldRegistry {
        boolean hasWaitingReader();
    }

    public interface LoanRepository {
        void save(Request request, Result result);
    }

    private final CatalogAvailability catalog;
    private final HoldRegistry holds;
    private final LoanRepository loans;

    public LibraryService(CatalogAvailability catalog, HoldRegistry holds, LoanRepository loans) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "Залежність не задано: catalog");
        this.holds = java.util.Objects.requireNonNull(holds, "Залежність не задано: holds");
        this.loans = java.util.Objects.requireNonNull(loans, "Залежність не задано: loans");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.days(), 1, 29, "Строк позики");
        checkRange(request.currentLoans(), 0, 10, "Кількість позик");
        if (request.renewal() && request.currentLoans() == 0) {
            throw new IllegalArgumentException("Немає чинної позики для продовження");
        }
        if (!request.renewal() && request.currentLoans() >= 5) {
            return rejected("Ліміт позик");
        }
        if (request.referenceBook() && (request.renewal() && request.days() > 7)) {
            return rejected("Обмеження довідкового видання");
        }
        if (request.renewal()) {
            if (holds.hasWaitingReader()) {
                return rejected("Є черга");
            }
        } else {
            int copies = catalog.availableCopies();
            if (copies < 0) {
                throw new IllegalStateException("Від’ємна кількість примірників");
            }
            if (copies == 0) {
                return rejected("Немає примірника");
            }
        }
        long fee = Math.max(0, request.days() - 15) * 100L;
        int count = request.renewal() ? request.currentLoans() : request.currentLoans() + 1;
        Result result = new Result(request.renewal() ? "Продовжено" : "Видано", request.days(), fee, count);
        loans.save(request, result);
        return result;
    }

    private static Result rejected(String status) {
        return new Result(status, 0, 0, 0);
    }

    private static void checkRange(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Недопустиме значення: " + field);
        }
    }
}
