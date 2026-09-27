package ua.course.courses;
import java.sql.*;
import java.util.Objects;
public class CourseRepository {
    private final Connection connection;
    public CourseRepository(Connection connection) { this.connection=Objects.requireNonNull(connection); }
    public Course create(Course item) throws SQLException {
        try(var q=Sql.command(connection,"INSERT INTO courses(code,price,capacity,level) VALUES (?,?,?,?) RETURNING id,code,price,capacity,level",item.code(),item.price(),item.capacity(),item.level());var r=q.executeQuery()) { r.next();return new Course(r.getLong(1),r.getString(2),r.getLong(3),r.getInt(4),r.getInt(5)); }
    }
    public Course findById(long id) throws SQLException {
        try(var q=Sql.command(connection,"SELECT id,code,price,capacity,level FROM courses WHERE id=?",id);var r=q.executeQuery()) { return r.next()?new Course(r.getLong(1),r.getString(2),r.getLong(3),r.getInt(4),r.getInt(5)):null; }
    }
    public void update(Course item) throws SQLException {
        try(var q=Sql.command(connection,"UPDATE courses SET code=?,price=?,capacity=?,level=? WHERE id=?",item.code(),item.price(),item.capacity(),item.level(),item.id())) {
            if(q.executeUpdate()==0) throw new IllegalArgumentException("Курс не знайдено");
        }
    }
    public void delete(long id) throws SQLException {
        try(var q=Sql.command(connection,"DELETE FROM courses WHERE id=?",id)) { q.executeUpdate(); }
    }
}
