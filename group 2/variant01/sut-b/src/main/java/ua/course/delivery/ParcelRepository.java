package ua.course.delivery;
import java.sql.*;
import java.util.Objects;
public class ParcelRepository {
    private final Connection connection;
    public ParcelRepository(Connection connection) { this.connection=Objects.requireNonNull(connection); }
    public Parcel create(Parcel p) throws SQLException {
        try(var q=Sql.command(connection,"INSERT INTO parcels(code,weight_grams,declared_value,fragile) VALUES (?,?,?,?) RETURNING id",p.code(),p.weightGrams(),p.declaredValue(),p.fragile());var r=q.executeQuery()) {
            r.next();return new Parcel(r.getLong(1),p.code(),p.weightGrams(),p.declaredValue(),p.fragile());
        }
    }
    public Parcel findById(long id) throws SQLException {
        try(var q=Sql.command(connection,"SELECT id,code,weight_grams,declared_value,fragile FROM parcels WHERE id=?",id);var r=q.executeQuery()) {
            return r.next()?new Parcel(r.getLong(1),r.getString(2),r.getInt(3),r.getLong(4),r.getBoolean(5)):null;
        }
    }
    public void update(Parcel p) throws SQLException {
        try(var q=Sql.command(connection,"UPDATE parcels SET code=?,weight_grams=?,declared_value=?,fragile=? WHERE id=?",p.code(),p.weightGrams(),p.declaredValue(),p.fragile(),p.id())) {
            if(q.executeUpdate()==0) throw new IllegalArgumentException("Посилку не знайдено");
        }
    }
    public void delete(long id) throws SQLException {
        try(var q=Sql.command(connection,"DELETE FROM parcels WHERE id=?",id)) { q.executeUpdate(); }
    }
}
