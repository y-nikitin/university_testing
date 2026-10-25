using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Courses;
using Npgsql;
using Testcontainers.PostgreSql;
using Xunit;

namespace Courses.Regression;

public class RegressionDatabase : IAsyncLifetime {
    public PostgreSqlContainer Container { get; } = new PostgreSqlBuilder().WithImage("postgres:17.6-alpine").Build();
    public NpgsqlConnection Connection { get; private set; } = null!;
    public long EnrollmentId;
    public async Task InitializeAsync() {
        try {
            await Container.StartAsync();
            Connection = new NpgsqlConnection(Container.GetConnectionString());
            await Connection.OpenAsync();
            using var command = new NpgsqlCommand(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "migrations/V1__create_tables.sql")), Connection);
            await command.ExecuteNonQueryAsync();
        } catch {
            if (Connection != null) await Connection.DisposeAsync();
            await Container.DisposeAsync();
            throw;
        }
    }
    public async Task DisposeAsync() {
        await Connection.DisposeAsync();
        await Container.DisposeAsync();
    }
}

public class EnrollmentRegression : IClassFixture<RegressionDatabase> {
    private readonly RegressionDatabase db;
    private readonly StudentRepository students;
    private readonly CourseRepository courses;
    private readonly EnrollmentRepository enrollments;
    private readonly EnrollmentService service;
    public EnrollmentRegression(RegressionDatabase db) {
        this.db = db;
        students = new StudentRepository(db.Connection);
        courses = new CourseRepository(db.Connection);
        enrollments = new EnrollmentRepository(db.Connection);
        service = new EnrollmentService(students, courses, enrollments, new Policy());
    }
    private class Policy : IEnrollmentPolicy {
        public bool IsAllowed(Student student, Course course) => true;
    }
    private class DeniedPolicy : IEnrollmentPolicy {
        public bool IsAllowed(Student student, Course course) => false;
    }
    private class DelayedPolicy : IEnrollmentPolicy {
        public bool IsAllowed(Student student, Course course) {
            Thread.Sleep(int.Parse(Environment.GetEnvironmentVariable("REGRESSION_POLICY_DELAY_MS") ?? "200"));
            return true;
        }
    }
    private Student Student() => students.Create(new Student(0, Guid.NewGuid().ToString(), 20, 90, false));
    private Course Course() => courses.Create(new Course(0, Guid.NewGuid().ToString(), 100000, 10, 1));
    private EnrollmentRequest Request(Student student, Course course) => new(Guid.NewGuid().ToString(), student.Id, course.Id);

    [Fact(DisplayName = "Зарахування зберігається у БД")]
    public void CreateEnrollment() {
        var saved = service.Enroll(Request(Student(), Course()));
        db.EnrollmentId = saved.Id;
        Assert.Equal(EnrollmentStatus.Enrolled, saved.Status);
        Assert.Equal(40000L, saved.Prepaid);
        Assert.Equal(saved, enrollments.FindById(saved.Id));
    }
    [Fact(DisplayName = "Скасування повертає передоплату")]
    public void CancelEnrollment() {
        var cancelled = service.Cancel(db.EnrollmentId);
        Assert.Equal(EnrollmentStatus.Cancelled, cancelled.Status);
        Assert.Equal(cancelled.Prepaid, cancelled.Refund);
        Assert.Equal(cancelled, enrollments.FindById(db.EnrollmentId));
    }
    [Fact(DisplayName = "Картка слухача зберігає вік")]
    public void StudentAge() {
        var saved = students.Create(new Student(0, "REGRESSION-STUDENT", 20, 90, false));
        Assert.Equal(20, students.FindById(saved.Id)!.Age);
    }
    [Fact(DisplayName = "Картка слухача зберігає вступний бал")]
    public void StudentScore() {
        var saved = students.Create(new Student(0, "REGRESSION-STUDENT", 25, 85, true));
        Assert.Equal(85, students.FindById(saved.Id)!.EntryScore);
    }
    [Fact(DisplayName = "Зарахування після зовнішнього дозволу")]
    public async Task EnrollmentAfterPolicyCheck() {
        var course = Course();
        var request = Request(Student(), course);
        var operation = Task.Run(() => {
            using var connection = new NpgsqlConnection(db.Container.GetConnectionString());
            connection.Open();
            var delayed = new EnrollmentService(new StudentRepository(connection), new CourseRepository(connection),
                new EnrollmentRepository(connection), new DelayedPolicy());
            return delayed.Enroll(request);
        });
        try {
            await Task.Delay(50);
            var saved = enrollments.FindEnrollments(course.Id, EnrollmentStatus.Enrolled);
            Assert.Single(saved);
            Assert.Equal(request.Reference, saved[0].Reference);
            Assert.Equal(100000L, saved[0].Total);
        } finally {
            await operation;
        }
    }
    [Fact(DisplayName = "Вибірка містить дві заявки курсу")]
    public void EnrollmentSelection() {
        var course = Course();
        var prefix = Guid.NewGuid().ToString()[..8];
        var references = new[] { prefix + "-A", prefix + "-B" };
        var fixtureOrder = Environment.GetEnvironmentVariable("REGRESSION_REVERSE_FIXTURE") == "true"
            ? references.Reverse() : references.AsEnumerable();
        foreach (var reference in fixtureOrder) service.Enroll(new EnrollmentRequest(reference, Student().Id, course.Id));
        using var command = new NpgsqlCommand("SELECT reference FROM enrollments WHERE course_id=@id", db.Connection);
        command.Parameters.AddWithValue("id", course.Id);
        using var rows = command.ExecuteReader();
        var actual = new List<string>();
        while (rows.Read()) actual.Add(rows.GetString(0));
        Assert.Equal(references, actual);
    }
    [Fact(DisplayName = "Завершення зберігає стан заявки")]
    public void Completion() {
        var saved = service.Enroll(Request(Student(), Course()));
        var completed = service.Complete(saved.Id);
        Assert.Equal(EnrollmentStatus.Completed, completed.Status);
        Assert.Equal(completed, enrollments.FindById(saved.Id));
    }
    [Fact(DisplayName = "Повторне зарахування відхиляється")]
    public void DuplicateEnrollment() {
        var student = Student(); var course = Course();
        var saved = service.Enroll(Request(student, course));
        Assert.Throws<EnrollmentRejectedException>(() => service.Enroll(Request(student, course)));
        Assert.Equal(saved, enrollments.FindById(saved.Id));
        Assert.Equal(1L, enrollments.CountOccupied(course.Id));
    }
    [Fact(DisplayName = "Зовнішня відмова не створює заявку")]
    public void PolicyRejection() {
        var course = Course(); var request = Request(Student(), course);
        var denied = new EnrollmentService(students, courses, enrollments, new DeniedPolicy());
        Assert.Throws<EnrollmentRejectedException>(() => denied.Enroll(request));
        Assert.Empty(enrollments.FindEnrollments(course.Id, EnrollmentStatus.Enrolled));
    }
    [Fact(DisplayName = "Повторне скасування не змінює заявку")]
    public void RepeatedCancellation() {
        var saved = service.Enroll(Request(Student(), Course()));
        var cancelled = service.Cancel(saved.Id);
        Assert.Throws<EnrollmentRejectedException>(() => service.Cancel(saved.Id));
        Assert.Equal(cancelled, enrollments.FindById(saved.Id));
    }
}
