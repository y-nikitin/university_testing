package ua.course.rental;

import java.sql.*;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Repository
public class EquipmentRepository {
    private final JdbcTemplate jdbc;

    @Autowired
    public EquipmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // З’єднання закриває його власник.
    public EquipmentRepository(Connection connection) {
        this.jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }

    public Equipment create(Equipment item) throws SQLException {
        long id = jdbc.queryForObject("INSERT INTO equipment(code,daily_rate,stock) VALUES (?,?,?) RETURNING id",
                Long.class, item.code(), item.dailyRate(), item.stock());
        return new Equipment(id, item.code(), item.dailyRate(), item.stock());
    }

    public Equipment findById(long id) throws SQLException {
        return jdbc.query("SELECT id,code,daily_rate,stock FROM equipment WHERE id=?",
                (r, index) -> new Equipment(r.getLong("id"), r.getString("code"), r.getLong("daily_rate"), r.getInt("stock")),
                id).stream().findFirst().orElse(null);
    }

    public void update(Equipment item) throws SQLException {
        if (jdbc.update("UPDATE equipment SET code=?,daily_rate=?,stock=? WHERE id=?",
                item.code(), item.dailyRate(), item.stock(), item.id()) == 0)
            throw new ResourceNotFoundException("Обладнання не знайдено");
    }

    public void delete(long id) throws SQLException {
        jdbc.update("DELETE FROM equipment WHERE id=?", id);
    }
}
