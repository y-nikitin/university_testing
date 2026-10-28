package ua.course.courses;
@FunctionalInterface
public interface EnrollmentPolicy { boolean isAllowed(Student student,Course course); }
