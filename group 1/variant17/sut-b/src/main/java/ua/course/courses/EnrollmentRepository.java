package ua.course.courses;
import java.sql.*;
import java.util.*;
public class EnrollmentRepository {
    private final Connection connection;
    public EnrollmentRepository(Connection connection) { this.connection=Objects.requireNonNull(connection); }
    public Enrollment create(Enrollment e) throws SQLException {
        try(var q=Sql.command(connection,"INSERT INTO enrollments(reference,student_id,course_id,total,prepaid,refund,status) VALUES (?,?,?,?,?,?,?) RETURNING *",e.reference(),e.studentId(),e.courseId(),e.total(),e.prepaid(),e.refund(),e.status().name());var r=q.executeQuery()) {
            r.next();return map(r);
        }
    }
    public Enrollment findById(long id) throws SQLException {
        try(var q=Sql.command(connection,"SELECT * FROM enrollments WHERE id=?",id);var r=q.executeQuery()) { return r.next()?map(r):null; }
    }
    public List<Enrollment> findEnrollments(long courseId,EnrollmentStatus status) throws SQLException {
        if(courseId<=0 || status==null) throw new IllegalArgumentException("Некоректний фільтр");
        try(var q=Sql.command(connection,"SELECT * FROM enrollments WHERE course_id=? AND status=? ORDER BY id",courseId,status.name());var r=q.executeQuery()) {
            var result=new ArrayList<Enrollment>();while(r.next()) result.add(map(r));return result;
        }
    }
    public long countOccupied(long courseId) throws SQLException {
        try(var q=Sql.command(connection,"SELECT count(*) FROM enrollments WHERE course_id=? AND status <> 'Cancelled'",courseId);var r=q.executeQuery()) { r.next();return r.getLong(1); }
    }
    public boolean hasEnrollment(long studentId,long courseId) throws SQLException {
        try(var q=Sql.command(connection,"SELECT EXISTS(SELECT 1 FROM enrollments WHERE student_id=? AND course_id=? AND status <> 'Cancelled')",studentId,courseId);var r=q.executeQuery()) { r.next();return r.getBoolean(1); }
    }
    public void updateStatus(long id,EnrollmentStatus next,long refund) throws SQLException {
        if((next!=EnrollmentStatus.Completed && next!=EnrollmentStatus.Cancelled) || refund<0 || refund>10000000) throw new IllegalArgumentException("Некоректний статус або повернення");
        try(var q=Sql.command(connection,"UPDATE enrollments SET status=?,refund=? WHERE id=? AND status='Enrolled'",next.name(),refund,id)) {
            if(q.executeUpdate()==0) throw new EnrollmentRejectedException("Активну заявку не знайдено");
        }
    }
    public void delete(long id) throws SQLException {
        try(var q=Sql.command(connection,"DELETE FROM enrollments WHERE id=?",id)) { q.executeUpdate(); }
    }
    private Enrollment map(ResultSet r) throws SQLException {
        return new Enrollment(r.getLong("id"),r.getString("reference"),r.getLong("student_id"),r.getLong("course_id"),r.getLong("total"),r.getLong("prepaid"),r.getLong("refund"),EnrollmentStatus.valueOf(r.getString("status")));
    }
}
