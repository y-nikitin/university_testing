package ua.course.courses;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@TestMethodOrder(MethodOrderer.Random.class)
class EnrollmentRegressionIT {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

    static JdbcTemplate jdbc;
    static StudentRepository students;
    static CourseRepository courses;
    static EnrollmentRepository enrollments;
    static EnrollmentService service;
    static long enrollmentId;

    @BeforeAll
    static void prepareDatabase() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .executeInTransaction(false)
                .load()
                .migrate();
        var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(source);
        students = new StudentRepository(jdbc);
        courses = new CourseRepository(jdbc);
        enrollments = new EnrollmentRepository(jdbc);
        service = new EnrollmentService(students, courses, enrollments, (student, course) -> true);
    }

    private Student student(boolean regular) throws Exception {
        return students.create(new Student(0, UUID.randomUUID().toString(), 20, 90, regular));
    }

    private Course course() throws Exception {
        return courses.create(new Course(0, UUID.randomUUID().toString(), 100000, 10, 1));
    }

    private EnrollmentRequest request(Student student, Course course, boolean coupon) {
        return new EnrollmentRequest(UUID.randomUUID().toString(), student.id(), course.id(), coupon);
    }

    @Test
    @DisplayName("Зарахування зберігається у БД")
    void createEnrollment() throws Exception {
        var saved = service.enroll(request(student(false), course(), false));
        enrollmentId = saved.id();
        assertEquals(saved, enrollments.findById(saved.id()));
        assertEquals(EnrollmentStatus.Enrolled, saved.status());
    }

    @Test
    @DisplayName("Скасування повертає передоплату")
    void cancelEnrollment() throws Exception {
        var cancelled = service.cancel(enrollmentId, 7);
        assertEquals(EnrollmentStatus.Cancelled, cancelled.status());
        assertEquals(cancelled.prepaid(), cancelled.refund());
        assertEquals(cancelled, enrollments.findById(enrollmentId));
    }

    @Test
    @DisplayName("Картка слухача зберігає вік")
    void studentAge() throws Exception {
        var saved = students.create(new Student(0, "REGRESSION-STUDENT", 20, 90, false));
        assertEquals(20, students.findById(saved.id()).age());
    }

    @Test
    @DisplayName("Картка слухача зберігає вступний бал")
    void studentScore() throws Exception {
        var saved = students.create(new Student(0, "REGRESSION-STUDENT", 25, 85, true));
        assertEquals(85, students.findById(saved.id()).entryScore());
    }

    @Test
    @DisplayName("Зарахування після зовнішнього дозволу")
    void enrollmentAfterPolicyCheck() throws Exception {
        var course = course();
        var request = request(student(false), course, false);
        var delayedService = new EnrollmentService(students, courses, enrollments, (student, value) -> {
            try {
                Thread.sleep(Long.getLong("regression.policyDelayMs", 200L));
                return true;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Перевірку перервано", exception);
            }
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var operation = CompletableFuture.supplyAsync(() -> {
                try {
                    return delayedService.enroll(request);
                } catch (Exception exception) {
                    throw new CompletionException(exception);
                }
            }, executor);
            Thread.sleep(50);
            var saved = enrollments.findEnrollments(course.id(), EnrollmentStatus.Enrolled);
            assertEquals(1, saved.size());
            assertEquals(request.reference(), saved.getFirst().reference());
            assertEquals(100000, saved.getFirst().total());
        }
    }

    @Test
    @DisplayName("Вибірка містить дві заявки курсу")
    void enrollmentSelection() throws Exception {
        var course = course();
        String prefix = UUID.randomUUID().toString().substring(0, 8);
        var references = List.of(prefix + "-A", prefix + "-B");
        var fixtureOrder = Boolean.getBoolean("regression.reverseFixture") ? references.reversed() : references;
        for (String reference : fixtureOrder) {
            service.enroll(new EnrollmentRequest(reference, student(false).id(), course.id(), false));
        }
        var actual = jdbc.queryForList("SELECT reference FROM enrollments WHERE course_id=?", String.class, course.id());
        assertEquals(references, actual);
    }

    @Test
    @DisplayName("Навчання без знижки")
    void fullPrice() throws Exception {
        var saved = service.enroll(request(student(false), course(), false));
        assertEquals(100000, saved.total());
        assertEquals(100000, enrollments.findById(saved.id()).prepaid());
    }

    @Test
    @DisplayName("Знижка постійному слухачу")
    void regularDiscount() throws Exception {
        var saved = service.enroll(request(student(true), course(), false));
        assertEquals(92000, saved.total());
        assertEquals(92000, enrollments.findById(saved.id()).total());
    }

    @Test
    @DisplayName("Промокод має пріоритет над знижкою постійному слухачу")
    void couponPriority() throws Exception {
        var saved = service.enroll(request(student(true), course(), true));
        assertEquals(85000, saved.total());
        assertEquals(85000, enrollments.findById(saved.id()).total());
    }

    @Test
    @DisplayName("Повторне скасування не змінює заявку")
    void repeatedCancellation() throws Exception {
        var saved = service.enroll(request(student(false), course(), false));
        var cancelled = service.cancel(saved.id(), 7);
        assertThrows(EnrollmentRejectedException.class, () -> service.cancel(saved.id(), 7));
        assertEquals(cancelled, enrollments.findById(saved.id()));
    }
}
