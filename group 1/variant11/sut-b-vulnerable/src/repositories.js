"use strict";
const { Student, Course, Enrollment, EnrollmentStatus: S } = require("./models");
const { integer, EnrollmentRejectedError } = require("./validation");
function number(value) {
    const result = Number(value);
    if (!Number.isSafeInteger(result)) throw new RangeError("Число БД не є безпечним цілим");
    return result;
}
function student(r) { return new Student(number(r.id), r.code, r.age, r.entry_score, r.regular); }
function course(r) { return new Course(number(r.id), r.code, number(r.price), r.capacity, r.level); }
function enrollment(r) { return new Enrollment(number(r.id), r.reference, number(r.student_id), number(r.course_id), number(r.total), number(r.prepaid), number(r.refund), r.status); }
class StudentRepository {
    constructor(client) { this.client = client; }
    async create(s) {
        const r = await this.client.query("INSERT INTO students(code,age,entry_score,regular) VALUES ($1,$2,$3,$4) RETURNING *", [s.code,s.age,s.entryScore,s.regular]);
        return student(r.rows[0]);
    }
    async findById(id) {
        const r = await this.client.query("SELECT * FROM students WHERE id=$1", [id]);
        return r.rows.length ? student(r.rows[0]) : null;
    }
    async update(s) {
        const r = await this.client.query("UPDATE students SET code=$1,age=$2,entry_score=$3,regular=$4 WHERE id=$5", [s.code,s.age,s.entryScore,s.regular,s.id]);
        if (!r.rowCount) throw new RangeError("Слухача не знайдено");
    }
    async delete(id) { await this.client.query("DELETE FROM students WHERE id=$1", [id]); }
}
class CourseRepository {
    constructor(client) { this.client = client; }
    async create(c) {
        const r = await this.client.query("INSERT INTO courses(code,price,capacity,level) VALUES ($1,$2,$3,$4) RETURNING *", [c.code,c.price,c.capacity,c.level]);
        return course(r.rows[0]);
    }
    async findById(id) {
        const r = await this.client.query("SELECT * FROM courses WHERE id=$1", [id]);
        return r.rows.length ? course(r.rows[0]) : null;
    }
    async update(c) {
        const r = await this.client.query("UPDATE courses SET code=$1,price=$2,capacity=$3,level=$4 WHERE id=$5", [c.code,c.price,c.capacity,c.level,c.id]);
        if (!r.rowCount) throw new RangeError("Курс не знайдено");
    }
    async delete(id) { await this.client.query("DELETE FROM courses WHERE id=$1", [id]); }
}
class EnrollmentRepository {
    constructor(client) { this.client = client; }
    async create(e) {
        const r = await this.client.query("INSERT INTO enrollments(reference,student_id,course_id,total,prepaid,refund,status) VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING *",
            [e.reference,e.studentId,e.courseId,e.total,e.prepaid,e.refund,e.status]);
        return enrollment(r.rows[0]);
    }
    async findById(id) {
        const r = await this.client.query("SELECT * FROM enrollments WHERE id=$1", [id]);
        return r.rows.length ? enrollment(r.rows[0]) : null;
    }
    async findEnrollments(courseId, status) {
        integer(courseId, 1, Number.MAX_SAFE_INTEGER);
        if (!Object.values(S).includes(status)) throw new RangeError("Некоректний статус");
        const r = await this.client.query("SELECT * FROM enrollments WHERE course_id=$1 AND status=$2 ORDER BY id", [courseId,status]);
        return r.rows.map(enrollment);
    }
    async countOccupied(courseId) {
        const r = await this.client.query("SELECT count(*) AS count FROM enrollments WHERE course_id=$1 AND status <> 'Cancelled'", [courseId]);
        return number(r.rows[0].count);
    }
    async hasEnrollment(studentId, courseId) {
        const r = await this.client.query("SELECT EXISTS(SELECT 1 FROM enrollments WHERE student_id=$1 AND course_id=$2 AND status <> 'Cancelled') AS present", [studentId,courseId]);
        return r.rows[0].present;
    }
    async updateStatus(id, next, refund) {
        if (![S.COMPLETED,S.CANCELLED].includes(next)) throw new RangeError("Некоректний статус");
        integer(refund, 0, 10000000);
        const r = await this.client.query("UPDATE enrollments SET status=$1,refund=$2 WHERE id=$3 AND status='Enrolled'", [next,refund,id]);
        if (!r.rowCount) throw new EnrollmentRejectedError("Активну заявку не знайдено");
    }
    async delete(id) { await this.client.query("DELETE FROM enrollments WHERE id=$1", [id]); }
}
module.exports = { StudentRepository, CourseRepository, EnrollmentRepository };
