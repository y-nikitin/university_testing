using System;
namespace Courses;
public class EnrollmentRejectedException : Exception {
    public EnrollmentRejectedException(string message) : base(message) { }
}
public class EnrollmentService {
    private readonly IStudentRepository students;
    private readonly ICourseRepository courses;
    private readonly IEnrollmentRepository enrollments;
    private readonly IEnrollmentPolicy policy;
    public EnrollmentService(IStudentRepository students, ICourseRepository courses, IEnrollmentRepository enrollments, IEnrollmentPolicy policy) {
        this.students = students ?? throw new ArgumentNullException(nameof(students));
        this.courses = courses ?? throw new ArgumentNullException(nameof(courses));
        this.enrollments = enrollments ?? throw new ArgumentNullException(nameof(enrollments));
        this.policy = policy ?? throw new ArgumentNullException(nameof(policy));
    }
    public long CalculatePrepayment(long total, bool regular = false) {
        if (total < 100 || total > 10000000) throw new ArgumentOutOfRangeException(nameof(total));
        return Math.Min(200000, total * 40 / 100);
    }
    public Enrollment Enroll(EnrollmentRequest request) {
        if (request == null || string.IsNullOrWhiteSpace(request.Reference) || request.Reference.Length > 40 || request.StudentId <= 0 || request.CourseId <= 0)
            throw new ArgumentException("Некоректна заявка");
        var student = students.FindById(request.StudentId) ?? throw new ArgumentException("Слухача не знайдено");
        var course = courses.FindById(request.CourseId) ?? throw new ArgumentException("Курс не знайдено");
        if (course.Capacity < 1 || course.Capacity > 100) throw new ArgumentException("Некоректна місткість");
        long prepaid = CalculatePrepayment(course.Price, student.Regular);
        if (enrollments.HasEnrollment(student.Id, course.Id)) throw new EnrollmentRejectedException("Слухача вже зараховано або він завершив курс");
        if (enrollments.CountOccupied(course.Id) >= course.Capacity) throw new EnrollmentRejectedException("Вільних місць немає");
        if (!policy.IsAllowed(student, course)) throw new EnrollmentRejectedException("Зарахування не дозволено");
        return enrollments.Create(new Enrollment(0, request.Reference, student.Id, course.Id, course.Price, prepaid, 0, EnrollmentStatus.Enrolled));
    }
    public Enrollment Complete(long id) {
        var enrollment = Active(id);
        enrollments.UpdateStatus(id, EnrollmentStatus.Completed, enrollment.Refund);
        return enrollment with { Status = EnrollmentStatus.Completed };
    }
    public Enrollment Cancel(long id) {
        var enrollment = Active(id);
        enrollments.UpdateStatus(id, EnrollmentStatus.Cancelled, enrollment.Prepaid);
        return enrollment with { Status = EnrollmentStatus.Cancelled, Refund = enrollment.Prepaid };
    }
    private Enrollment Active(long id) {
        if (id <= 0) throw new ArgumentOutOfRangeException(nameof(id));
        var enrollment = enrollments.FindById(id) ?? throw new ArgumentException("Заявку не знайдено");
        if (enrollment.Status != EnrollmentStatus.Enrolled) throw new EnrollmentRejectedException("Зміна статусу заборонена");
        return enrollment;
    }
}
