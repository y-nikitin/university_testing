using System.Collections.Generic;
namespace Courses;
public enum EnrollmentStatus { Enrolled, Completed, Cancelled }
public record Student(long Id, string Code, int Age, int EntryScore, bool Regular = false);
public record Course(long Id, string Code, long Price, int Capacity, int Level = 1);
public record Enrollment(long Id, string Reference, long StudentId, long CourseId, long Total, long Prepaid, long Refund, EnrollmentStatus Status);
public record EnrollmentRequest(string Reference, long StudentId, long CourseId);
public interface IStudentRepository {
    Student Create(Student student);
    Student? FindById(long id);
    void Update(Student student);
    void Delete(long id);
}
public interface ICourseRepository {
    Course Create(Course course);
    Course? FindById(long id);
    void Update(Course course);
    void Delete(long id);
}
public interface IEnrollmentRepository {
    Enrollment Create(Enrollment enrollment);
    Enrollment? FindById(long id);
    List<Enrollment> FindEnrollments(long courseId, EnrollmentStatus status);
    long CountOccupied(long courseId);
    bool HasEnrollment(long studentId, long courseId);
    void UpdateStatus(long id, EnrollmentStatus next, long refund);
    void Delete(long id);
}
public interface IEnrollmentPolicy { bool IsAllowed(Student student, Course course); }
