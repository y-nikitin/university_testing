package ua.course.store;

import java.sql.*;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Repository
public class ProductRepository {
    private final JdbcTemplate jdbc;

    @Autowired
    public ProductRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // Сумісність із попередніми тестами: з’єднання закриває його власник.
    public ProductRepository(Connection connection) {
        this(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
    }

    public Product create(Product product) throws SQLException {
        return jdbc.queryForObject("INSERT INTO products(sku,unit_price,stock,on_sale) VALUES (?,?,?,?) RETURNING *",
                (r, index) -> map(r), product.sku(), product.unitPrice(), product.stock(), product.onSale());
    }
    public Product findById(long id) throws SQLException {
        return jdbc.query("SELECT * FROM products WHERE id=?", (r, index) -> map(r), id).stream().findFirst().orElse(null);
    }
    public void update(Product product) throws SQLException {
        if (jdbc.update("UPDATE products SET sku=?,unit_price=?,stock=?,on_sale=? WHERE id=?",
                product.sku(), product.unitPrice(), product.stock(), product.onSale(), product.id()) == 0)
            throw new ResourceNotFoundException("Товар не знайдено");
    }
    public void delete(long id) throws SQLException { jdbc.update("DELETE FROM products WHERE id=?", id); }

    private Product map(ResultSet r) throws SQLException {
        return new Product(r.getLong("id"), r.getString("sku"), r.getLong("unit_price"), r.getInt("stock"), r.getBoolean("on_sale"));
    }
}
