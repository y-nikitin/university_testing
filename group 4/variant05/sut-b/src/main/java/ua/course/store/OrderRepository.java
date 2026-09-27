package ua.course.store;
import java.sql.*;
import java.util.*;
public class OrderRepository {
    private final Connection connection;
    private static final String SELECT = "SELECT o.*,i.id AS item_id,i.product_id,i.quantity,i.unit_price,i.on_sale FROM orders o JOIN order_items i ON i.order_id=o.id ";
    public OrderRepository(Connection connection) { this.connection = Objects.requireNonNull(connection); }
    public Order create(Order order) throws SQLException {
        if (!connection.getAutoCommit()) throw new IllegalStateException("Для створення замовлення потрібне з'єднання з autoCommit=true");
        connection.setAutoCommit(false);
        Throwable failure = null;
        try {
            long id;
            try (var query = connection.prepareStatement("INSERT INTO orders(reference,premium,coupon_valid,total,status) VALUES (?,?,?,?,?) RETURNING id")) {
                query.setString(1,order.reference()); query.setBoolean(2,order.premium()); query.setBoolean(3,order.couponValid()); query.setLong(4,order.total()); query.setString(5,order.status().name());
                try (var result=query.executeQuery()) { result.next(); id=result.getLong(1); }
            }
            var item=order.item(); long itemId;
            try (var query = connection.prepareStatement("INSERT INTO order_items(order_id,product_id,quantity,unit_price,on_sale) VALUES (?,?,?,?,?) RETURNING id")) {
                query.setLong(1,id); query.setLong(2,item.productId()); query.setInt(3,item.quantity()); query.setLong(4,item.unitPrice()); query.setBoolean(5,item.onSale());
                try (var result=query.executeQuery()) { result.next(); itemId=result.getLong(1); }
            }
            var saved=new Order(id,order.reference(),order.premium(),order.couponValid(),order.total(),order.status(),new OrderItem(itemId,item.productId(),item.quantity(),item.unitPrice(),item.onSale()));
            connection.commit(); return saved;
        } catch (SQLException | RuntimeException | Error error) {
            failure=error;
            try { connection.rollback(); } catch (SQLException rollbackError) { error.addSuppressed(rollbackError); }
            throw error;
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException restoreError) {
                if (failure!=null) failure.addSuppressed(restoreError); else throw restoreError;
            }
        }
    }
    public Order findById(long id) throws SQLException {
        try (var query=connection.prepareStatement(SELECT+"WHERE o.id=?")) {
            query.setLong(1,id);
            try (var result=query.executeQuery()) { return result.next() ? map(result) : null; }
        }
    }
    public List<Order> findAll() throws SQLException {
        try (var query=connection.prepareStatement(SELECT+"ORDER BY o.id")) { return list(query); }
    }
    public List<Order> findOrders(long productId, OrderStatus status) throws SQLException {
        if (productId<=0 || status==null) throw new IllegalArgumentException("Некоректний фільтр");
        try (var query=connection.prepareStatement(SELECT+"WHERE i.product_id=? AND o.status=? ORDER BY o.id")) {
            query.setLong(1,productId); query.setString(2,status.name()); return list(query);
        }
    }
    public void updateStatus(long id, OrderStatus status) throws SQLException {
        if (status==null) throw new IllegalArgumentException("Статус обов'язковий");
        try (var query=connection.prepareStatement("UPDATE orders SET status=? WHERE id=?")) {
            query.setString(1,status.name()); query.setLong(2,id);
            if (query.executeUpdate()==0) throw new IllegalArgumentException("Замовлення не знайдено");
        }
    }
    public void delete(long id) throws SQLException {
        try (var query=connection.prepareStatement("DELETE FROM orders WHERE id=?")) { query.setLong(1,id); query.executeUpdate(); }
    }
    private List<Order> list(PreparedStatement query) throws SQLException {
        var orders=new ArrayList<Order>();
        try (var result=query.executeQuery()) { while(result.next()) orders.add(map(result)); }
        return orders;
    }
    private Order map(ResultSet r) throws SQLException {
        return new Order(r.getLong("id"),r.getString("reference"),r.getBoolean("premium"),r.getBoolean("coupon_valid"),r.getLong("total"),OrderStatus.valueOf(r.getString("status")),new OrderItem(r.getLong("item_id"),r.getLong("product_id"),r.getInt("quantity"),r.getLong("unit_price"),r.getBoolean("on_sale")));
    }
}
