package ua.course.courses;

import java.sql.*;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

@Repository
public class EnrollmentRepository {
    private final JdbcTemplate jdbc;
    @Autowired
    public EnrollmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    // З’єднання закриває його власник.
    public EnrollmentRepository(Connection connection) {
        this.jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
    }
    public Enrollment create(Enrollment e) throws SQLException {
        return jdbc.queryForObject("INSERT INTO enrollments(reference,student_id,course_id,total,prepaid,refund,status) VALUES (?,?,?,?,?,?,?) RETURNING *",
                (r, index) -> map(r), e.reference(), e.studentId(), e.courseId(), e.total(), e.prepaid(), e.refund(), e.status().name());
    }
    public Enrollment findById(long id) throws SQLException {
        return jdbc.query("SELECT * FROM enrollments WHERE id=?", (r, index) -> map(r), id).stream().findFirst().orElse(null);
    }
    public List<Enrollment> findEnrollments(long courseId, EnrollmentStatus status) throws SQLException {
        if (courseId <= 0 || status == null) throw new IllegalArgumentException("Некоректний фільтр");
        return jdbc.query("SELECT * FROM enrollments WHERE course_id=? AND status=? ORDER BY id", (r, index) -> map(r), courseId, status.name());
    }
    public long countOccupied(long courseId) throws SQLException {
        return jdbc.queryForObject("SELECT count(*) FROM enrollments WHERE course_id=? AND status <> 'Cancelled'", Long.class, courseId);
    }
    public boolean hasEnrollment(long studentId, long courseId) throws SQLException {
        return jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM enrollments WHERE student_id=? AND course_id=? AND status <> 'Cancelled')", Boolean.class, studentId, courseId);
    }
    public void updateStatus(long id, EnrollmentStatus next, long refund) throws SQLException {
        if ((next != EnrollmentStatus.Completed && next != EnrollmentStatus.Cancelled) || refund < 0 || refund > 10000000)
            throw new IllegalArgumentException("Некоректний статус або повернення");
        if (jdbc.update("UPDATE enrollments SET status=?,refund=? WHERE id=? AND status='Enrolled'", next.name(), refund, id) == 0)
            throw new EnrollmentRejectedException("Активну заявку не знайдено");
    }
    public void delete(long id) throws SQLException { jdbc.update("DELETE FROM enrollments WHERE id=?", id); }
    private Enrollment map(ResultSet r) throws SQLException {
        return new Enrollment(r.getLong("id"),r.getString("reference"),r.getLong("student_id"),r.getLong("course_id"),r.getLong("total"),r.getLong("prepaid"),r.getLong("refund"),EnrollmentStatus.valueOf(r.getString("status")));
    }
}
