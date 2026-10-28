package ua.course.courses;
public record Enrollment(long id,String reference,long studentId,long courseId,long total,long prepaid,long refund,EnrollmentStatus status) {}
