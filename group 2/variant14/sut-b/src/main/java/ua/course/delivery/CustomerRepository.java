package ua.course.delivery;
import java.sql.*;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Repository
public class CustomerRepository {
    private final JdbcTemplate jdbc;
    @Autowired
    public CustomerRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    // З’єднання закриває його власник.
    public CustomerRepository(Connection connection) { this.jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true)); }
    public Customer create(Customer c) throws SQLException {
        long id = jdbc.queryForObject("INSERT INTO customers(code,regular) VALUES (?,?) RETURNING id", Long.class, c.code(),c.regular());
        return new Customer(id,c.code(),c.regular());
    }
    public Customer findById(long id) throws SQLException {
        return jdbc.query("SELECT id,code,regular FROM customers WHERE id=?",
                (r, index) -> new Customer(r.getLong(1),r.getString(2),r.getBoolean(3)), id).stream().findFirst().orElse(null);
    }
    public void update(Customer c) throws SQLException {
        if (jdbc.update("UPDATE customers SET code=?,regular=? WHERE id=?", c.code(),c.regular(),c.id()) == 0)
            throw new ResourceNotFoundException("Клієнта не знайдено");
    }
    public void delete(long id) throws SQLException { jdbc.update("DELETE FROM customers WHERE id=?", id); }
}
