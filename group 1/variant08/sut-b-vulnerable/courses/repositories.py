from dataclasses import replace
from .models import Student, Course, Enrollment, EnrollmentStatus as S
from .service import integer, EnrollmentRejectedError

class PostgresStudentRepository:
    def __init__(self, connection):
        self.connection = connection

    def create(self, item):
        with self.connection.cursor() as c:
            c.execute("INSERT INTO students(code,age,entry_score,regular) VALUES (%s,%s,%s,%s) RETURNING id", (item.code,item.age,item.entry_score,item.regular,))
            return replace(item, id=c.fetchone()[0])

    def find_by_id(self, item_id):
        with self.connection.cursor() as c:
            c.execute("SELECT id,code,age,entry_score,regular FROM students WHERE id=%s", (item_id,))
            row = c.fetchone()
            return Student(*row) if row else None

    def update(self, item):
        with self.connection.cursor() as c:
            c.execute("UPDATE students SET code=%s,age=%s,entry_score=%s,regular=%s WHERE id=%s", (item.code,item.age,item.entry_score,item.regular,item.id))
            if c.rowcount == 0:
                raise ValueError("Слухача не знайдено")

    def delete(self, item_id):
        with self.connection.cursor() as c:
            c.execute("DELETE FROM students WHERE id=%s", (item_id,))

class PostgresCourseRepository:
    def __init__(self, connection):
        self.connection = connection

    def create(self, item):
        with self.connection.cursor() as c:
            c.execute("INSERT INTO courses(code,price,capacity,level) VALUES (%s,%s,%s,%s) RETURNING id", (item.code,item.price,item.capacity,item.level,))
            return replace(item, id=c.fetchone()[0])

    def find_by_id(self, item_id):
        with self.connection.cursor() as c:
            c.execute("SELECT id,code,price,capacity,level FROM courses WHERE id=%s", (item_id,))
            row = c.fetchone()
            return Course(*row) if row else None

    def update(self, item):
        with self.connection.cursor() as c:
            c.execute("UPDATE courses SET code=%s,price=%s,capacity=%s,level=%s WHERE id=%s", (item.code,item.price,item.capacity,item.level,item.id))
            if c.rowcount == 0:
                raise ValueError("Курс не знайдено")

    def delete(self, item_id):
        with self.connection.cursor() as c:
            c.execute("DELETE FROM courses WHERE id=%s", (item_id,))

class PostgresEnrollmentRepository:
    _select = "SELECT id,reference,student_id,course_id,total,prepaid,refund,status FROM enrollments "

    def __init__(self, connection):
        self.connection = connection

    def create(self, e):
        with self.connection.cursor() as c:
            c.execute("INSERT INTO enrollments(reference,student_id,course_id,total,prepaid,refund,status) VALUES (%s,%s,%s,%s,%s,%s,%s) RETURNING id",
                      (e.reference,e.student_id,e.course_id,e.total,e.prepaid,e.refund,e.status.value))
            return replace(e, id=c.fetchone()[0])

    @staticmethod
    def _map(row):
        return Enrollment(*row[:7], S(row[7]))

    def find_by_id(self, enrollment_id):
        with self.connection.cursor() as c:
            c.execute(self._select + "WHERE id=%s", (enrollment_id,))
            row = c.fetchone()
            return self._map(row) if row else None

    def find_enrollments(self, course_id, status):
        integer(course_id, 1, 2**63 - 1)
        if not isinstance(status, S):
            raise ValueError("Некоректний статус")
        with self.connection.cursor() as c:
            c.execute(self._select + "WHERE course_id=%s AND status=%s ORDER BY id", (course_id,status.value))
            return [self._map(row) for row in c.fetchall()]

    def count_occupied(self, course_id):
        with self.connection.cursor() as c:
            c.execute("SELECT count(*) FROM enrollments WHERE course_id=%s AND status <> 'Cancelled'", (course_id,))
            return c.fetchone()[0]

    def has_enrollment(self, student_id, course_id):
        with self.connection.cursor() as c:
            c.execute("SELECT EXISTS(SELECT 1 FROM enrollments WHERE student_id=%s AND course_id=%s AND status <> 'Cancelled')", (student_id,course_id))
            return c.fetchone()[0]

    def update_status(self, enrollment_id, next_status, refund):
        integer(refund, 0, 10000000)
        if not isinstance(next_status, S) or next_status not in (S.COMPLETED, S.CANCELLED):
            raise ValueError("Некоректний статус")
        with self.connection.cursor() as c:
            c.execute("UPDATE enrollments SET status=%s,refund=%s WHERE id=%s AND status='Enrolled'", (next_status.value,refund,enrollment_id))
            if c.rowcount == 0:
                raise EnrollmentRejectedError("Активну заявку не знайдено")

    def delete(self, enrollment_id):
        with self.connection.cursor() as c:
            c.execute("DELETE FROM enrollments WHERE id=%s", (enrollment_id,))
