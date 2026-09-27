package ua.course.store;
import java.sql.SQLException;
import java.util.Objects;
public class OrderService {
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderPolicy policy;
    public OrderService(ProductRepository products, OrderRepository orders, OrderPolicy policy) {
        this.products=Objects.requireNonNull(products); this.orders=Objects.requireNonNull(orders); this.policy=Objects.requireNonNull(policy);
    }
    public long calculateTotal(long unitPrice, int quantity) {
        return calculateTotal(unitPrice, quantity, false, false, false);
    }
    public long calculateTotal(long unitPrice, int quantity, boolean premium, boolean couponValid, boolean onSale) {
        if (unitPrice<100 || unitPrice>1000000) throw new IllegalArgumentException("Ціна поза допустимим діапазоном");
        validateQuantity(quantity);
        long subtotal = unitPrice * quantity;
        int discountPercent = subtotal >= 220000 ? 16 : subtotal >= 70000 ? 7 : 0;
        return subtotal - subtotal * discountPercent / 100;
    }
    public Order placeOrder(OrderRequest request) throws SQLException {
        if (request==null || request.reference()==null || request.reference().isBlank() || request.reference().length()>40 || request.productId()<=0)
            throw new IllegalArgumentException("Некоректна заявка");
        validateQuantity(request.quantity());
        var product=products.findById(request.productId());
        if (product==null) throw new IllegalArgumentException("Товар не знайдено");
        if (request.quantity()>product.stock()) throw new OrderRejectedException("Недостатньо товару");
        if (!policy.isAllowed(product,request.quantity())) throw new OrderRejectedException("Оформлення не дозволено");
        long total=calculateTotal(product.unitPrice(),request.quantity(),request.premium(),request.couponValid(),product.onSale());
        var item=new OrderItem(0,product.id(),request.quantity(),product.unitPrice(),product.onSale());
        return orders.create(new Order(0,request.reference(),request.premium(),request.couponValid(),total,OrderStatus.CONFIRMED,item));
    }
    public Order returnOrder(long id) throws SQLException {
        if (id<=0) throw new IllegalArgumentException("Некоректний ідентифікатор");
        var order=orders.findById(id);
        if (order==null) throw new IllegalArgumentException("Замовлення не знайдено");
        if (order.status()==OrderStatus.RETURNED) throw new OrderRejectedException("Замовлення вже повернено");
        orders.updateStatus(id,OrderStatus.RETURNED);
        return new Order(order.id(),order.reference(),order.premium(),order.couponValid(),order.total(),OrderStatus.RETURNED,order.item());
    }
    private void validateQuantity(int quantity) {
        if (quantity<1 || quantity>20) throw new IllegalArgumentException("Кількість має бути від 1 до 20");
    }
}
