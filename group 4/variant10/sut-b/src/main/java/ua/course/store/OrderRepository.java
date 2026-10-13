package ua.course.store;

import java.sql.*;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class OrderRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Connection suppliedConnection;
    private static final String SELECT = "SELECT o.*,i.id AS item_id,i.product_id,i.quantity,i.unit_price,i.on_sale FROM orders o JOIN order_items i ON i.order_id=o.id ";

    @Autowired
    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        this.suppliedConnection = null;
    }

    // Сумісність із попередніми тестами: з’єднання закриває його власник.
    public OrderRepository(Connection connection) {
        var source = new SingleConnectionDataSource(connection, true);
        this.jdbc = new JdbcTemplate(source);
        this.transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        this.suppliedConnection = connection;
    }

    public Order create(Order order) throws SQLException {
        if (suppliedConnection != null && !suppliedConnection.getAutoCommit())
            throw new IllegalStateException("Для створення замовлення потрібне з'єднання з autoCommit=true");
        return transaction.execute(status -> {
            long id = jdbc.queryForObject("INSERT INTO orders(reference,premium,coupon_valid,total,status) VALUES (?,?,?,?,?) RETURNING id",
                    Long.class, order.reference(), order.premium(), order.couponValid(), order.total(), order.status().name());
            var item = order.item();
            long itemId = jdbc.queryForObject("INSERT INTO order_items(order_id,product_id,quantity,unit_price,on_sale) VALUES (?,?,?,?,?) RETURNING id",
                    Long.class, id, item.productId(), item.quantity(), item.unitPrice(), item.onSale());
            return new Order(id, order.reference(), order.premium(), order.couponValid(), order.total(), order.status(),
                    new OrderItem(itemId, item.productId(), item.quantity(), item.unitPrice(), item.onSale()));
        });
    }
    public Order findById(long id) throws SQLException {
        return jdbc.query(SELECT + "WHERE o.id=?", (r, index) -> map(r), id).stream().findFirst().orElse(null);
    }
    public List<Order> findAll() throws SQLException {
        return jdbc.query(SELECT + "ORDER BY o.id", (r, index) -> map(r));
    }
    public List<Order> findOrders(long productId, OrderStatus status) throws SQLException {
        if (productId <= 0 || status == null) throw new IllegalArgumentException("Некоректний фільтр");
        return jdbc.query(SELECT + "WHERE i.product_id=? AND o.status=? ORDER BY o.id", (r, index) -> map(r), productId, status.name());
    }
    public void updateStatus(long id, OrderStatus status) throws SQLException {
        if (status == null) throw new IllegalArgumentException("Статус обов'язковий");
        if (jdbc.update("UPDATE orders SET status=? WHERE id=?", status.name(), id) == 0)
            throw new ResourceNotFoundException("Замовлення не знайдено");
    }
    public void delete(long id) throws SQLException { jdbc.update("DELETE FROM orders WHERE id=?", id); }

    private Order map(ResultSet r) throws SQLException {
        return new Order(r.getLong("id"), r.getString("reference"), r.getBoolean("premium"), r.getBoolean("coupon_valid"),
                r.getLong("total"), OrderStatus.valueOf(r.getString("status")),
                new OrderItem(r.getLong("item_id"), r.getLong("product_id"), r.getInt("quantity"), r.getLong("unit_price"), r.getBoolean("on_sale")));
    }
}
