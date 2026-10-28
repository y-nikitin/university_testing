"use strict";
const { Enrollment, EnrollmentRequest, EnrollmentStatus: S } = require("./models");
const { integer, EnrollmentRejectedError } = require("./validation");
class ResourceNotFoundError extends RangeError {}
class EnrollmentService {
    constructor(students, courses, enrollments, policy) {
        if (!students || !courses || !enrollments || !policy) throw new TypeError("Усі залежності обов'язкові");
        Object.assign(this, { students, courses, enrollments, policy });
    }
    calculateRefund(prepaid, daysBeforeStart) {
        integer(prepaid, 0, 10000000); integer(daysBeforeStart, 0, 36500);
        return daysBeforeStart >= 7 ? prepaid : daysBeforeStart >= 2 ? Math.floor(prepaid / 2) : 0;
    }
    async enroll(request) {
        if (!(request instanceof EnrollmentRequest)) throw new TypeError("Некоректна заявка");
        if (typeof request.reference !== "string" || !request.reference.trim() || request.reference.length > 40)
            throw new RangeError("Код заявки має містити 1–40 символів");
        integer(request.studentId, 1, Number.MAX_SAFE_INTEGER); integer(request.courseId, 1, Number.MAX_SAFE_INTEGER);
        const student = await this.students.findById(request.studentId);
        if (!student) throw new ResourceNotFoundError("Слухача не знайдено");
        const course = await this.courses.findById(request.courseId);
        if (!course) throw new ResourceNotFoundError("Курс не знайдено");
        integer(course.price, 100, 10000000); integer(course.capacity, 1, 100);
        if (await this.enrollments.hasEnrollment(student.id, course.id)) throw new EnrollmentRejectedError("Слухача вже зараховано або він завершив курс");
        if (await this.enrollments.countOccupied(course.id) >= course.capacity) throw new EnrollmentRejectedError("Вільних місць немає");
        const allowed = await this.policy.isAllowed(student, course);
        if (typeof allowed !== "boolean") throw new TypeError("Некоректна відповідь зовнішньої перевірки");
        if (!allowed) throw new EnrollmentRejectedError("Зарахування не дозволено");
        return await this.enrollments.create(new Enrollment(0, request.reference, student.id, course.id, course.price, course.price, 0, S.ENROLLED));
    }
    async complete(id) {
        const enrollment = await this.active(id);
        await this.enrollments.updateStatus(id, S.COMPLETED, enrollment.refund);
        return new Enrollment(enrollment.id, enrollment.reference, enrollment.studentId, enrollment.courseId,
            enrollment.total, enrollment.prepaid, enrollment.refund, S.COMPLETED);
    }
    async cancel(id, daysBeforeStart) {
        integer(daysBeforeStart, 0, 36500);
        const enrollment = await this.active(id);
        const refund = this.calculateRefund(enrollment.prepaid, daysBeforeStart);
        await this.enrollments.updateStatus(id, S.CANCELLED, refund);
        return new Enrollment(enrollment.id, enrollment.reference, enrollment.studentId, enrollment.courseId,
            enrollment.total, enrollment.prepaid, refund, S.CANCELLED);
    }
    async active(id) {
        integer(id, 1, Number.MAX_SAFE_INTEGER);
        const enrollment = await this.enrollments.findById(id);
        if (!enrollment) throw new ResourceNotFoundError("Заявку не знайдено");
        if (enrollment.status !== S.ENROLLED) throw new EnrollmentRejectedError("Зміна статусу заборонена");
        return enrollment;
    }
}
module.exports = { EnrollmentService, EnrollmentRejectedError, ResourceNotFoundError };
