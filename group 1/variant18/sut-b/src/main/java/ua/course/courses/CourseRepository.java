package ua.course.courses;

import java.sql.*;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Repository
public class CourseRepository {
    private final JdbcTemplate jdbc;
    @Autowired
    public CourseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    // З’єднання закриває його власник.
    public CourseRepository(Connection connection) {
        this.jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }
    public Course create(Course item) throws SQLException {
        long id = jdbc.queryForObject("INSERT INTO courses(code,price,capacity,level) VALUES (?,?,?,?) RETURNING id", Long.class, item.code(),item.price(),item.capacity(),item.level());
        return new Course(id, item.code(),item.price(),item.capacity(),item.level());
    }
    public Course findById(long id) throws SQLException {
        return jdbc.query("SELECT id,code,price,capacity,level FROM courses WHERE id=?",
                (r, index) -> new Course(r.getLong(1),r.getString(2),r.getLong(3),r.getInt(4),r.getInt(5)), id).stream().findFirst().orElse(null);
    }
    public void update(Course item) throws SQLException {
        if (jdbc.update("UPDATE courses SET code=?,price=?,capacity=?,level=? WHERE id=?", item.code(),item.price(),item.capacity(),item.level(),item.id()) == 0)
            throw new ResourceNotFoundException("Курс не знайдено");
    }
    public void delete(long id) throws SQLException { jdbc.update("DELETE FROM courses WHERE id=?", id); }
}
