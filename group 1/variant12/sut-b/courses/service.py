from dataclasses import replace
from .models import Enrollment, EnrollmentRequest, EnrollmentStatus as S
from .ports import StudentRepository, CourseRepository, EnrollmentRepository, EnrollmentPolicy

class EnrollmentRejectedError(Exception):
    pass

def integer(value, low, high):
    if type(value) is not int or not low <= value <= high:
        raise ValueError("Ціле число поза допустимими межами")

def boolean(value):
    if type(value) is not bool:
        raise ValueError("Потрібна логічна ознака bool")

class EnrollmentService:
    def __init__(self, students: StudentRepository, courses: CourseRepository, enrollments: EnrollmentRepository, policy: EnrollmentPolicy):
        if any(value is None for value in (students, courses, enrollments, policy)):
            raise ValueError("Усі залежності обов'язкові")
        self.students, self.courses, self.enrollments, self.policy = students, courses, enrollments, policy

    def calculate_total(self, price, regular=False, coupon_valid=False):
        integer(price, 100, 10000000)
        boolean(regular)
        boolean(coupon_valid)
        discount = 0
        return price - discount

    def is_eligible(self, age, entry_score, level):
        integer(age, 14, 100)
        integer(entry_score, 0, 100)
        integer(level, 1, 3)
        return level == 1 or (level == 2 and entry_score >= 60) or (level == 3 and age >= 18 and entry_score >= 80)

    def calculate_refund(self, prepaid, days_before_start):
        integer(prepaid, 0, 10000000)
        integer(days_before_start, 0, 36500)
        return prepaid

    def enroll(self, request):
        if not isinstance(request, EnrollmentRequest):
            raise ValueError("Некоректна заявка")
        if not isinstance(request.reference, str) or not request.reference.strip() or len(request.reference) > 40:
            raise ValueError("Код заявки має містити 1–40 символів")
        integer(request.student_id, 1, 2**63 - 1)
        integer(request.course_id, 1, 2**63 - 1)
        boolean(request.coupon_valid)
        student = self.students.find_by_id(request.student_id)
        if student is None:
            raise ValueError("Слухача не знайдено")
        course = self.courses.find_by_id(request.course_id)
        if course is None:
            raise ValueError("Курс не знайдено")
        integer(course.capacity, 1, 100)
        total = self.calculate_total(course.price, student.regular, request.coupon_valid)
        if self.enrollments.has_enrollment(student.id, course.id):
            raise EnrollmentRejectedError("Слухача вже зараховано або він завершив курс")
        if self.enrollments.count_occupied(course.id) >= course.capacity:
            raise EnrollmentRejectedError("Вільних місць немає")
        if not self.is_eligible(student.age, student.entry_score, course.level):
            raise EnrollmentRejectedError("Персональні умови допуску не виконані")
        allowed = self.policy.is_allowed(student, course)
        boolean(allowed)
        if not allowed:
            raise EnrollmentRejectedError("Зарахування не дозволено")
        return self.enrollments.create(Enrollment(0, request.reference, student.id, course.id, total, total, 0, S.ENROLLED))

    def complete(self, enrollment_id):
        enrollment = self._active(enrollment_id)
        self.enrollments.update_status(enrollment_id, S.COMPLETED, enrollment.refund)
        return replace(enrollment, status=S.COMPLETED)

    def cancel(self, enrollment_id, days_before_start=0):
        integer(days_before_start, 0, 36500)
        enrollment = self._active(enrollment_id)
        refund = self.calculate_refund(enrollment.prepaid, days_before_start)
        self.enrollments.update_status(enrollment_id, S.CANCELLED, refund)
        return replace(enrollment, status=S.CANCELLED, refund=refund)

    def _active(self, enrollment_id):
        integer(enrollment_id, 1, 2**63 - 1)
        enrollment = self.enrollments.find_by_id(enrollment_id)
        if enrollment is None:
            raise ValueError("Заявку не знайдено")
        if enrollment.status != S.ENROLLED:
            raise EnrollmentRejectedError("Зміна статусу заборонена")
        return enrollment
