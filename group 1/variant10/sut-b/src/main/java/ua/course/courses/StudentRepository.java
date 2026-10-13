package ua.course.courses;

import java.sql.*;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Repository
public class StudentRepository {
    private final JdbcTemplate jdbc;
    @Autowired
    public StudentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    // З’єднання закриває його власник.
    public StudentRepository(Connection connection) {
        this.jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }
    public Student create(Student item) throws SQLException {
        long id = jdbc.queryForObject("INSERT INTO students(code,age,entry_score,regular) VALUES (?,?,?,?) RETURNING id", Long.class, item.code(),item.age(),item.entryScore(),item.regular());
        return new Student(id, item.code(),item.age(),item.entryScore(),item.regular());
    }
    public Student findById(long id) throws SQLException {
        return jdbc.query("SELECT id,code,age,entry_score,regular FROM students WHERE id=?",
                (r, index) -> new Student(r.getLong(1),r.getString(2),r.getInt(3),r.getInt(4),r.getBoolean(5)), id).stream().findFirst().orElse(null);
    }
    public void update(Student item) throws SQLException {
        if (jdbc.update("UPDATE students SET code=?,age=?,entry_score=?,regular=? WHERE id=?", item.code(),item.age(),item.entryScore(),item.regular(),item.id()) == 0)
            throw new ResourceNotFoundException("Слухача не знайдено");
    }
    public void delete(long id) throws SQLException { jdbc.update("DELETE FROM students WHERE id=?", id); }
}
