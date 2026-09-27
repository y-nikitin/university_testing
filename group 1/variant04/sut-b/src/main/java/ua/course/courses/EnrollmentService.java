package ua.course.courses;
import java.sql.SQLException;
import java.util.Objects;
public class EnrollmentService {
    private final StudentRepository students;
    private final CourseRepository courses;
    private final EnrollmentRepository enrollments;
    private final EnrollmentPolicy policy;
    public EnrollmentService(StudentRepository students,CourseRepository courses,EnrollmentRepository enrollments,EnrollmentPolicy policy) {
        this.students=Objects.requireNonNull(students);this.courses=Objects.requireNonNull(courses);
        this.enrollments=Objects.requireNonNull(enrollments);this.policy=Objects.requireNonNull(policy);
    }
    public long calculateTotal(long price,boolean regular,boolean couponValid) {
        if(price<100 || price>10000000) throw new IllegalArgumentException("Некоректна ціна");
        int percent=price>=300000?12:price>=100000?5:0;
        return price-price*percent/100;
    }
    public boolean isEligible(int age,int entryScore,int level,boolean regular) {
        if(age<14 || age>100 || entryScore<0 || entryScore>100 || level<1 || level>3) throw new IllegalArgumentException("Некоректні параметри допуску");
        return true;
    }
    public long calculateRefund(long prepaid,int daysBeforeStart) {
        if(prepaid<0 || prepaid>10000000 || daysBeforeStart<0 || daysBeforeStart>36500) throw new IllegalArgumentException("Некоректні параметри повернення");
        return prepaid;
    }
    public Enrollment enroll(EnrollmentRequest request) throws SQLException {
        if(request==null || request.reference()==null || request.reference().isBlank() || request.reference().length()>40 || request.studentId()<=0 || request.courseId()<=0)
            throw new IllegalArgumentException("Некоректна заявка");
        var student=students.findById(request.studentId());
        if(student==null) throw new IllegalArgumentException("Слухача не знайдено");
        var course=courses.findById(request.courseId());
        if(course==null) throw new IllegalArgumentException("Курс не знайдено");
        if(course.capacity()<1 || course.capacity()>100) throw new IllegalArgumentException("Некоректна місткість");
        long total=calculateTotal(course.price(),student.regular(),request.couponValid());
        if(enrollments.hasEnrollment(student.id(),course.id())) throw new EnrollmentRejectedException("Слухача вже зараховано або він завершив курс");
        if(enrollments.countOccupied(course.id())>=course.capacity()) throw new EnrollmentRejectedException("Вільних місць немає");
        if(!isEligible(student.age(),student.entryScore(),course.level(),student.regular())) throw new EnrollmentRejectedException("Персональні умови допуску не виконані");
        if(!policy.isAllowed(student,course)) throw new EnrollmentRejectedException("Зарахування не дозволено");
        return enrollments.create(new Enrollment(0,request.reference(),student.id(),course.id(),total,total,0,EnrollmentStatus.Enrolled));
    }
    public Enrollment complete(long id) throws SQLException {
        var e=active(id);
        enrollments.updateStatus(id,EnrollmentStatus.Completed,e.refund());
        return new Enrollment(e.id(),e.reference(),e.studentId(),e.courseId(),e.total(),e.prepaid(),e.refund(),EnrollmentStatus.Completed);
    }
    public Enrollment cancel(long id,int daysBeforeStart) throws SQLException {
        if(daysBeforeStart<0 || daysBeforeStart>36500) throw new IllegalArgumentException("Некоректний строк скасування");
        var e=active(id);
        long refund=calculateRefund(e.prepaid(),daysBeforeStart);
        enrollments.updateStatus(id,EnrollmentStatus.Cancelled,refund);
        return new Enrollment(e.id(),e.reference(),e.studentId(),e.courseId(),e.total(),e.prepaid(),refund,EnrollmentStatus.Cancelled);
    }
    private Enrollment active(long id) throws SQLException {
        if(id<=0) throw new IllegalArgumentException("Некоректний ідентифікатор");
        var e=enrollments.findById(id);
        if(e==null) throw new IllegalArgumentException("Заявку не знайдено");
        if(e.status()!=EnrollmentStatus.Enrolled) throw new EnrollmentRejectedException("Зміна статусу заборонена");
        return e;
    }
}
