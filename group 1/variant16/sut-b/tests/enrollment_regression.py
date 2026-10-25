import os
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from uuid import uuid4

import psycopg
import pytest
from testcontainers.postgres import PostgresContainer

from courses.models import Student, Course, EnrollmentRequest, EnrollmentStatus as S
from courses.repositories import PostgresStudentRepository, PostgresCourseRepository, PostgresEnrollmentRepository
from courses.service import EnrollmentService, EnrollmentRejectedError


enrollment_id = 0


class Policy:
    def is_allowed(self, student, course):
        return True


@pytest.fixture(scope="module")
def sut():
    with PostgresContainer("postgres:17.6-alpine") as postgres:
        with psycopg.connect(host=postgres.get_container_host_ip(), port=postgres.get_exposed_port(5432),
                             user=postgres.username, password=postgres.password,
                             dbname=postgres.dbname, autocommit=True) as db:
            migration = Path(__file__).resolve().parents[1] / "migrations/V1__create_tables.sql"
            db.execute(migration.read_text(encoding="utf-8"))
            students = PostgresStudentRepository(db)
            courses = PostgresCourseRepository(db)
            enrollments = PostgresEnrollmentRepository(db)
            service = EnrollmentService(students, courses, enrollments, Policy())
            yield db, students, courses, enrollments, service


def student(sut):
    return sut[1].create(Student(0, str(uuid4()), 20, 90, False))


def course(sut):
    return sut[2].create(Course(0, str(uuid4()), 100000, 10, 1))


def request(person, training):
    return EnrollmentRequest(str(uuid4()), person.id, training.id, False)


def test_create_enrollment(sut):
    global enrollment_id
    saved = sut[4].enroll(request(student(sut), course(sut)))
    enrollment_id = saved.id
    assert saved.status == S.ENROLLED
    assert sut[3].find_by_id(saved.id) == saved


def test_cancel_enrollment(sut):
    cancelled = sut[4].cancel(enrollment_id, 7)
    assert cancelled.status == S.CANCELLED
    assert cancelled.refund == 75000
    assert sut[3].find_by_id(enrollment_id) == cancelled


def test_student_age(sut):
    saved = sut[1].create(Student(0, "REGRESSION-STUDENT", 20, 90, False))
    assert sut[1].find_by_id(saved.id).age == 20


def test_student_score(sut):
    saved = sut[1].create(Student(0, "REGRESSION-STUDENT", 25, 85, True))
    assert sut[1].find_by_id(saved.id).entry_score == 85


def test_enrollment_after_policy_check(sut):
    class DelayedPolicy:
        def is_allowed(self, person, training):
            time.sleep(float(os.getenv("REGRESSION_POLICY_DELAY_MS", "200")) / 1000)
            return True

    training = course(sut)
    data = request(student(sut), training)
    service = EnrollmentService(sut[1], sut[2], sut[3], DelayedPolicy())
    with ThreadPoolExecutor(max_workers=1) as executor:
        operation = executor.submit(service.enroll, data)
        time.sleep(0.05)
        saved = sut[3].find_enrollments(training.id, S.ENROLLED)
        assert len(saved) == 1
        assert saved[0].reference == data.reference
        assert saved[0].total == 100000


def test_enrollment_selection(sut):
    training = course(sut)
    prefix = str(uuid4())[:8]
    references = [prefix + "-A", prefix + "-B"]
    fixture_order = list(reversed(references)) if os.getenv("REGRESSION_REVERSE_FIXTURE") == "true" else references
    for reference in fixture_order:
        sut[4].enroll(EnrollmentRequest(reference, student(sut).id, training.id, False))
    actual = [row[0] for row in sut[0].execute("SELECT reference FROM enrollments WHERE course_id=%s", (training.id,))]
    assert actual == references


def test_completion(sut):
    saved = sut[4].enroll(request(student(sut), course(sut)))
    completed = sut[4].complete(saved.id)
    assert completed.status == S.COMPLETED
    assert sut[3].find_by_id(saved.id) == completed


def test_duplicate_enrollment(sut):
    person, training = student(sut), course(sut)
    saved = sut[4].enroll(request(person, training))
    with pytest.raises(EnrollmentRejectedError):
        sut[4].enroll(request(person, training))
    assert sut[3].find_by_id(saved.id) == saved
    assert sut[3].count_occupied(training.id) == 1


def test_policy_rejection(sut):
    class DeniedPolicy:
        def is_allowed(self, person, training):
            return False

    training = course(sut)
    service = EnrollmentService(sut[1], sut[2], sut[3], DeniedPolicy())
    with pytest.raises(EnrollmentRejectedError):
        service.enroll(request(student(sut), training))
    assert sut[3].find_enrollments(training.id, S.ENROLLED) == []


def test_repeated_cancellation(sut):
    saved = sut[4].enroll(request(student(sut), course(sut)))
    cancelled = sut[4].cancel(saved.id, 7)
    with pytest.raises(EnrollmentRejectedError):
        sut[4].cancel(saved.id, 7)
    assert sut[3].find_by_id(saved.id) == cancelled
