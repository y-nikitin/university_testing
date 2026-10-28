"use strict";
const EnrollmentStatus = Object.freeze({ ENROLLED: "Enrolled", COMPLETED: "Completed", CANCELLED: "Cancelled" });
class Student {
    constructor(id, code, age, entryScore, regular = false) {
        Object.assign(this, { id, code, age, entryScore, regular }); Object.freeze(this);
    }
}
class Course {
    constructor(id, code, price, capacity, level = 1) {
        Object.assign(this, { id, code, price, capacity, level }); Object.freeze(this);
    }
}
class Enrollment {
    constructor(id, reference, studentId, courseId, total, prepaid, refund, status) {
        Object.assign(this, { id, reference, studentId, courseId, total, prepaid, refund, status }); Object.freeze(this);
    }
}
class EnrollmentRequest {
    constructor(reference, studentId, courseId) { Object.assign(this, { reference, studentId, courseId }); Object.freeze(this); }
}
module.exports = { Student, Course, Enrollment, EnrollmentRequest, EnrollmentStatus };
