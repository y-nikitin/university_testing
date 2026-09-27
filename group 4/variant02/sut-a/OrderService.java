public final class OrderService {
    public record Request(int quantity, int unitPrice, boolean premium, boolean couponClaimed) {}

    public record Result(String status, int quantity, int discountPercent, long goods, long delivery, long total) {}

    public interface StockProvider {
        int availableUnits();
    }

    public interface CouponValidator {
        boolean isValid();
    }

    public interface OrderRepository {
        void save(Request request, Result result);
    }

    private final StockProvider stock;
    private final CouponValidator coupons;
    private final OrderRepository orders;

    public OrderService(StockProvider stock, CouponValidator coupons, OrderRepository orders) {
        this.stock = java.util.Objects.requireNonNull(stock, "Залежність не задано: stock");
        this.coupons = java.util.Objects.requireNonNull(coupons, "Залежність не задано: coupons");
        this.orders = java.util.Objects.requireNonNull(orders, "Залежність не задано: orders");
    }

    public Result process(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Заявку не задано");
        }
        checkRange(request.quantity(), 1, 19, "Кількість товару");
        checkRange(request.unitPrice(), 100, 100_000, "Ціна одиниці");
        int available = stock.availableUnits();
        if (available < 0) {
            throw new IllegalStateException("Від’ємний залишок товару");
        }
        if (available < request.quantity()) {
            return rejected("Недостатньо товару");
        }
        boolean coupon = request.couponClaimed() && coupons.isValid();
        int discount;
        if (request.premium() && coupon) {
            discount = 15;
        } else if (request.premium()) {
            discount = 10;
        } else if (coupon) {
            discount = 5;
        } else {
            discount = 0;
        }
        long subtotal = request.quantity() * (long) request.unitPrice();
        long goods = subtotal - subtotal * discount / 100;
        long delivery = goods > 50_000 ? 0 : 5_000;
        Result result = new Result("Оформлено", request.quantity(), discount, goods, delivery, goods + delivery);
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
