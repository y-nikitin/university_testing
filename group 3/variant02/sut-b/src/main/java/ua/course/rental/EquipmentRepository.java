package ua.course.rental;
import java.sql.*;
import java.util.Objects;
public class EquipmentRepository {
    private final Connection connection;
    public EquipmentRepository(Connection connection) { this.connection=Objects.requireNonNull(connection); }
    public Equipment create(Equipment item) throws SQLException {
        try (var query=connection.prepareStatement("INSERT INTO equipment(code,daily_rate,stock) VALUES (?,?,?) RETURNING id")) {
            bind(query,item);
            try (var result=query.executeQuery()) { result.next(); return new Equipment(result.getLong(1),item.code(),item.dailyRate(),item.stock()); }
        }
    }
    public Equipment findById(long id) throws SQLException {
        try (var query=connection.prepareStatement("SELECT id,code,daily_rate,stock FROM equipment WHERE id=?")) {
            query.setLong(1,id);
            try (var r=query.executeQuery()) { return r.next()?new Equipment(r.getLong(1),r.getString(2),r.getLong(3),r.getInt(4)):null; }
        }
    }
    public void update(Equipment item) throws SQLException {
        try (var query=connection.prepareStatement("UPDATE equipment SET code=?,daily_rate=?,stock=? WHERE id=?")) {
            bind(query,item);query.setLong(4,item.id());
            if (query.executeUpdate()==0) throw new IllegalArgumentException("Обладнання не знайдено");
        }
    }
    public void delete(long id) throws SQLException {
        try (var query=connection.prepareStatement("DELETE FROM equipment WHERE id=?")) { query.setLong(1,id);query.executeUpdate(); }
    }
    private void bind(PreparedStatement query,Equipment item) throws SQLException {
        query.setString(1,item.code());query.setLong(2,item.dailyRate());query.setInt(3,item.stock());
    }
}
