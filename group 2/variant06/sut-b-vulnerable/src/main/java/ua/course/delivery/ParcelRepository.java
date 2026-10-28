package ua.course.delivery;
import java.sql.*;
import java.util.Objects;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Repository
public class ParcelRepository {
    private final JdbcTemplate jdbc;
    @Autowired
    public ParcelRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    // З’єднання закриває його власник.
    public ParcelRepository(Connection connection) { this.jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true)); }
    public Parcel create(Parcel p) throws SQLException {
        long id = jdbc.queryForObject("INSERT INTO parcels(code,weight_grams,declared_value,fragile) VALUES (?,?,?,?) RETURNING id", Long.class, p.code(),p.weightGrams(),p.declaredValue(),p.fragile());
        return new Parcel(id,p.code(),p.weightGrams(),p.declaredValue(),p.fragile());
    }
    public Parcel findById(long id) throws SQLException {
        return jdbc.query("SELECT id,code,weight_grams,declared_value,fragile FROM parcels WHERE id=?",
                (r, index) -> new Parcel(r.getLong(1),r.getString(2),r.getInt(3),r.getLong(4),r.getBoolean(5)), id).stream().findFirst().orElse(null);
    }
    public void update(Parcel p) throws SQLException {
        if (jdbc.update("UPDATE parcels SET code=?,weight_grams=?,declared_value=?,fragile=? WHERE id=?", p.code(),p.weightGrams(),p.declaredValue(),p.fragile(),p.id()) == 0)
            throw new ResourceNotFoundException("Посилку не знайдено");
    }
    public void delete(long id) throws SQLException { jdbc.update("DELETE FROM parcels WHERE id=?", id); }
}
