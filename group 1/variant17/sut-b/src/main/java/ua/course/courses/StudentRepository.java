package ua.course.courses;
import java.sql.*;
import java.util.Objects;
public class StudentRepository {
    private final Connection connection;
    public StudentRepository(Connection connection) { this.connection=Objects.requireNonNull(connection); }
    public Student create(Student item) throws SQLException {
        try(var q=Sql.command(connection,"INSERT INTO students(code,age,entry_score,regular) VALUES (?,?,?,?) RETURNING id,code,age,entry_score,regular",item.code(),item.age(),item.entryScore(),item.regular());var r=q.executeQuery()) { r.next();return new Student(r.getLong(1),r.getString(2),r.getInt(3),r.getInt(4),r.getBoolean(5)); }
    }
    public Student findById(long id) throws SQLException {
        try(var q=Sql.command(connection,"SELECT id,code,age,entry_score,regular FROM students WHERE id=?",id);var r=q.executeQuery()) { return r.next()?new Student(r.getLong(1),r.getString(2),r.getInt(3),r.getInt(4),r.getBoolean(5)):null; }
    }
    public void update(Student item) throws SQLException {
        try(var q=Sql.command(connection,"UPDATE students SET code=?,age=?,entry_score=?,regular=? WHERE id=?",item.code(),item.age(),item.entryScore(),item.regular(),item.id())) {
            if(q.executeUpdate()==0) throw new IllegalArgumentException("Слухача не знайдено");
        }
    }
    public void delete(long id) throws SQLException {
        try(var q=Sql.command(connection,"DELETE FROM students WHERE id=?",id)) { q.executeUpdate(); }
    }
}
