BEGIN;
CREATE TABLE students (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE CHECK(length(trim(code)) > 0),
 age INTEGER NOT NULL CHECK(age BETWEEN 14 AND 100),
 entry_score INTEGER NOT NULL CHECK(entry_score BETWEEN 0 AND 100),
 regular BOOLEAN NOT NULL
);
CREATE TABLE courses (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 code VARCHAR(40) NOT NULL UNIQUE CHECK(length(trim(code)) > 0),
 price BIGINT NOT NULL CHECK(price BETWEEN 100 AND 10000000),
 capacity INTEGER NOT NULL CHECK(capacity BETWEEN 1 AND 100),
 level INTEGER NOT NULL CHECK(level BETWEEN 1 AND 3)
);
CREATE TABLE enrollments (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 reference VARCHAR(40) NOT NULL UNIQUE CHECK(length(trim(reference)) > 0),
 student_id BIGINT NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
 course_id BIGINT NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
 total BIGINT NOT NULL CHECK(total BETWEEN 0 AND 10000000),
 prepaid BIGINT NOT NULL CHECK(prepaid BETWEEN 0 AND total),
 refund BIGINT NOT NULL CHECK(refund BETWEEN 0 AND prepaid),
 status VARCHAR(16) NOT NULL CHECK(status IN ('Enrolled','Completed','Cancelled'))
);
CREATE UNIQUE INDEX enrollments_one_place ON enrollments(student_id,course_id) WHERE status <> 'Cancelled';
CREATE INDEX enrollments_course_status ON enrollments(course_id,status,id);
COMMIT;
