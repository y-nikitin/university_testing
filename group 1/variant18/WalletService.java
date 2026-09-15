public final class WalletService {
    public record Request(int amount, int dailyTransferred, boolean internalNetwork, boolean trustedRecipient) {}

    public record Result(String status, long amount, long fee, long balance, long dailyTransferred) {}

    public interface WalletState {
        String currentStatus();
    }

    public interface BalanceProvider {
        long currentBalance();
    }

    public interface TransferRepository {
        void save(Request request, Result result);
    }

    private final WalletState wallet;
    private final BalanceProvider balances;
    private final TransferRepository transfers;

    public WalletService(WalletState wallet, BalanceProvider balances, TransferRepository transfers) {
        this.wallet = java.util.Objects.requireNonNull(wallet, "Залежність не задано: wallet");
        this.balances = java.util.Objects.requireNonNull(balances, "Залежність не задано: balances");
        this.transfers = java.util.Objects.requireNonNull(transfers, "Залежність не задано: transfers");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.amount(), 100, 1_000_000, "Сума переказу");
        checkRange(request.dailyTransferred(), 0, 2_000_000, "Добова сума");
        String state = wallet.currentStatus();
        if ("Заблокований".equals(state)) {
            return rejected("Гаманець заблокований");
        }
        if ("Закритий".equals(state)) {
            return rejected("Гаманець закритий");
        }
        if (!"Активний".equals(state)) {
            throw new IllegalStateException("Невідомий стан гаманця");
        }
        long daily = (long) request.dailyTransferred() + request.amount();
        if (daily >= 2_000_000) {
            return rejected("Перевищено добовий ліміт");
        }
        int basisPoints;
        if (request.internalNetwork() && request.trustedRecipient()) {
            basisPoints = 0;
        } else if (request.internalNetwork()) {
            basisPoints = 100;
        } else if (request.trustedRecipient()) {
            basisPoints = 100;
        } else {
            basisPoints = 200;
        }
        long fee = basisPoints == 0 ? 0 : Math.max(100, request.amount() * (long) basisPoints / 10_000);
        long balance = balances.currentBalance();
        if (balance < 0) {
            throw new IllegalStateException("Від’ємний баланс гаманця");
        }
        long debit = request.amount() + fee;
        if (balance <= debit) {
            return rejected("Недостатньо коштів");
        }
        Result result = new Result("Виконано", request.amount(), fee, balance - debit, daily);
        transfers.save(request, result);
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
