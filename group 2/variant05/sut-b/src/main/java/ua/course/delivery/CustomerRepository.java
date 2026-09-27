package ua.course.delivery;
import java.sql.*;
import java.util.Objects;
public class CustomerRepository {
    private final Connection connection;
    public CustomerRepository(Connection connection) { this.connection=Objects.requireNonNull(connection); }
    public Customer create(Customer c) throws SQLException {
        try(var q=Sql.command(connection,"INSERT INTO customers(code,regular) VALUES (?,?) RETURNING id",c.code(),c.regular());var r=q.executeQuery()) {
            r.next();return new Customer(r.getLong(1),c.code(),c.regular());
        }
    }
    public Customer findById(long id) throws SQLException {
        try(var q=Sql.command(connection,"SELECT id,code,regular FROM customers WHERE id=?",id);var r=q.executeQuery()) {
            return r.next()?new Customer(r.getLong(1),r.getString(2),r.getBoolean(3)):null;
        }
    }
    public void update(Customer c) throws SQLException {
        try(var q=Sql.command(connection,"UPDATE customers SET code=?,regular=? WHERE id=?",c.code(),c.regular(),c.id())) {
            if(q.executeUpdate()==0) throw new IllegalArgumentException("Клієнта не знайдено");
        }
    }
    public void delete(long id) throws SQLException {
        try(var q=Sql.command(connection,"DELETE FROM customers WHERE id=?",id)) { q.executeUpdate(); }
    }
}
