package ua.course.store;
import java.sql.*;
import java.util.Objects;
public class ProductRepository {
    private final Connection connection;
    public ProductRepository(Connection connection) { this.connection = Objects.requireNonNull(connection); }
    public Product create(Product product) throws SQLException {
        try (var query = connection.prepareStatement("INSERT INTO products(sku,unit_price,stock,on_sale) VALUES (?,?,?,?) RETURNING id")) {
            bind(query, product);
            try (var result = query.executeQuery()) { result.next(); return new Product(result.getLong(1),product.sku(),product.unitPrice(),product.stock(),product.onSale()); }
        }
    }
    public Product findById(long id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT * FROM products WHERE id=?")) {
            query.setLong(1,id);
            try (var result = query.executeQuery()) { return result.next() ? new Product(result.getLong("id"),result.getString("sku"),result.getLong("unit_price"),result.getInt("stock"),result.getBoolean("on_sale")) : null; }
        }
    }
    public void update(Product product) throws SQLException {
        try (var query = connection.prepareStatement("UPDATE products SET sku=?,unit_price=?,stock=?,on_sale=? WHERE id=?")) {
            bind(query,product); query.setLong(5,product.id());
            if (query.executeUpdate()==0) throw new IllegalArgumentException("Товар не знайдено");
        }
    }
    public void delete(long id) throws SQLException {
        try (var query = connection.prepareStatement("DELETE FROM products WHERE id=?")) { query.setLong(1,id); query.executeUpdate(); }
    }
    private void bind(PreparedStatement query, Product product) throws SQLException {
        query.setString(1,product.sku()); query.setLong(2,product.unitPrice()); query.setInt(3,product.stock()); query.setBoolean(4,product.onSale());
    }
}
