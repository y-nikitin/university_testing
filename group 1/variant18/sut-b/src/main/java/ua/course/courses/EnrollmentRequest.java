package ua.course.courses;
public record EnrollmentRequest(String reference,long studentId,long courseId,boolean couponValid) {}
