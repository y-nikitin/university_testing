from dataclasses import dataclass
from enum import StrEnum

class EnrollmentStatus(StrEnum):
    ENROLLED = "Enrolled"
    COMPLETED = "Completed"
    CANCELLED = "Cancelled"

@dataclass(frozen=True)
class Student:
    id: int
    code: str
    age: int
    entry_score: int
    regular: bool = False

@dataclass(frozen=True)
class Course:
    id: int
    code: str
    price: int
    capacity: int
    level: int = 1

@dataclass(frozen=True)
class Enrollment:
    id: int
    reference: str
    student_id: int
    course_id: int
    total: int
    prepaid: int
    refund: int
    status: EnrollmentStatus

@dataclass(frozen=True)
class EnrollmentRequest:
    reference: str
    student_id: int
    course_id: int
    coupon_valid: bool = False
