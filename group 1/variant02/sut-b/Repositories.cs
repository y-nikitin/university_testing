using System;
using System.Collections.Generic;
using System.Data.Common;
namespace Courses;
internal static class Sql {
    internal static DbCommand Command(DbConnection connection, string sql, params object[] values) {
        var command = connection.CreateCommand(); command.CommandText = sql;
        for (int i = 0; i < values.Length; i++) {
            var parameter = command.CreateParameter(); parameter.ParameterName = "p" + i; parameter.Value = values[i]; command.Parameters.Add(parameter);
        }
        return command;
    }
}
public class StudentRepository : IStudentRepository {
    private readonly DbConnection connection;
    public StudentRepository(DbConnection connection) { this.connection = connection ?? throw new ArgumentNullException(nameof(connection)); }
    public Student Create(Student item) {
        using var command = Sql.Command(connection, "INSERT INTO students(code,age,entry_score,regular) VALUES (@p0,@p1,@p2,@p3) RETURNING id",item.Code,item.Age,item.EntryScore,item.Regular);
        return item with { Id = Convert.ToInt64(command.ExecuteScalar()) };
    }
    public Student? FindById(long id) {
        using var command = Sql.Command(connection, "SELECT id,code,age,entry_score,regular FROM students WHERE id=@p0",id);
        using var r = command.ExecuteReader(); return r.Read() ? new Student(r.GetInt64(0),r.GetString(1),r.GetInt32(2),r.GetInt32(3),r.GetBoolean(4)) : null;
    }
    public void Update(Student item) {
        using var command = Sql.Command(connection, "UPDATE students SET code=@p0,age=@p1,entry_score=@p2,regular=@p3 WHERE id=@p4",item.Code,item.Age,item.EntryScore,item.Regular,item.Id);
        if (command.ExecuteNonQuery() == 0) throw new ArgumentException("Слухача не знайдено");
    }
    public void Delete(long id) {
        using var command = Sql.Command(connection, "DELETE FROM students WHERE id=@p0",id); command.ExecuteNonQuery();
    }
}
public class CourseRepository : ICourseRepository {
    private readonly DbConnection connection;
    public CourseRepository(DbConnection connection) { this.connection = connection ?? throw new ArgumentNullException(nameof(connection)); }
    public Course Create(Course item) {
        using var command = Sql.Command(connection, "INSERT INTO courses(code,price,capacity,level) VALUES (@p0,@p1,@p2,@p3) RETURNING id",item.Code,item.Price,item.Capacity,item.Level);
        return item with { Id = Convert.ToInt64(command.ExecuteScalar()) };
    }
    public Course? FindById(long id) {
        using var command = Sql.Command(connection, "SELECT id,code,price,capacity,level FROM courses WHERE id=@p0",id);
        using var r = command.ExecuteReader(); return r.Read() ? new Course(r.GetInt64(0),r.GetString(1),r.GetInt64(2),r.GetInt32(3),r.GetInt32(4)) : null;
    }
    public void Update(Course item) {
        using var command = Sql.Command(connection, "UPDATE courses SET code=@p0,price=@p1,capacity=@p2,level=@p3 WHERE id=@p4",item.Code,item.Price,item.Capacity,item.Level,item.Id);
        if (command.ExecuteNonQuery() == 0) throw new ArgumentException("Курс не знайдено");
    }
    public void Delete(long id) {
        using var command = Sql.Command(connection, "DELETE FROM courses WHERE id=@p0",id); command.ExecuteNonQuery();
    }
}
public class EnrollmentRepository : IEnrollmentRepository {
    private readonly DbConnection connection;
    private const string Select = "SELECT id,reference,student_id,course_id,total,prepaid,refund,status FROM enrollments ";
    public EnrollmentRepository(DbConnection connection) { this.connection = connection ?? throw new ArgumentNullException(nameof(connection)); }
    public Enrollment Create(Enrollment e) {
        using var command = Sql.Command(connection,"INSERT INTO enrollments(reference,student_id,course_id,total,prepaid,refund,status) VALUES (@p0,@p1,@p2,@p3,@p4,@p5,@p6) RETURNING id",e.Reference,e.StudentId,e.CourseId,e.Total,e.Prepaid,e.Refund,e.Status.ToString());
        return e with { Id = Convert.ToInt64(command.ExecuteScalar()) };
    }
    public Enrollment? FindById(long id) {
        using var command = Sql.Command(connection,Select + "WHERE id=@p0",id);
        using var r = command.ExecuteReader(); return r.Read() ? Map(r) : null;
    }
    public List<Enrollment> FindEnrollments(long courseId, EnrollmentStatus status) {
        if (courseId <= 0 || !Enum.IsDefined(typeof(EnrollmentStatus),status)) throw new ArgumentException("Некоректний фільтр");
        using var command = Sql.Command(connection,Select + "WHERE course_id=@p0 AND status=@p1 ORDER BY id",courseId,status.ToString());
        using var r = command.ExecuteReader(); var result = new List<Enrollment>();
        while (r.Read()) result.Add(Map(r)); return result;
    }
    public long CountOccupied(long courseId) {
        using var command = Sql.Command(connection,"SELECT count(*) FROM enrollments WHERE course_id=@p0 AND status <> 'Cancelled'",courseId);
        return Convert.ToInt64(command.ExecuteScalar());
    }
    public bool HasEnrollment(long studentId, long courseId) {
        using var command = Sql.Command(connection,"SELECT EXISTS(SELECT 1 FROM enrollments WHERE student_id=@p0 AND course_id=@p1 AND status <> 'Cancelled')",studentId,courseId);
        return Convert.ToBoolean(command.ExecuteScalar());
    }
    public void UpdateStatus(long id, EnrollmentStatus next, long refund) {
        if ((next != EnrollmentStatus.Completed && next != EnrollmentStatus.Cancelled) || refund < 0 || refund > 10000000)
            throw new ArgumentException("Некоректний статус або сума повернення");
        using var command = Sql.Command(connection,"UPDATE enrollments SET status=@p0,refund=@p1 WHERE id=@p2 AND status='Enrolled'",next.ToString(),refund,id);
        if (command.ExecuteNonQuery() == 0) throw new EnrollmentRejectedException("Активну заявку не знайдено");
    }
    public void Delete(long id) {
        using var command = Sql.Command(connection,"DELETE FROM enrollments WHERE id=@p0",id); command.ExecuteNonQuery();
    }
    private static Enrollment Map(DbDataReader r) => new Enrollment(r.GetInt64(0),r.GetString(1),r.GetInt64(2),r.GetInt64(3),r.GetInt64(4),r.GetInt64(5),r.GetInt64(6),Enum.Parse<EnrollmentStatus>(r.GetString(7)));
}
