import os
from pathlib import Path
import psycopg
from fastapi import Depends, FastAPI, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from starlette.exceptions import HTTPException
from .database import connect
from .models import Student, Course, EnrollmentRequest
from .repositories import PostgresStudentRepository, PostgresCourseRepository, PostgresEnrollmentRepository
from .service import EnrollmentService, EnrollmentRejectedError, ResourceNotFoundError
from .http_enrollment_policy import HttpEnrollmentPolicy, EnrollmentPolicyError

app = FastAPI(title="Навчальні курси")

class StudentInput(BaseModel):
    code: str
    age: int
    entry_score: int
    regular: bool = False

class CourseInput(BaseModel):
    code: str
    price: int
    capacity: int
    level: int = 1

class EnrollmentInput(BaseModel):
    reference: str
    student_id: int
    course_id: int
    coupon_valid: bool = False

class CancelInput(BaseModel):
    days_before_start: int

def connection():
    with connect() as db:
        yield db

def students(db=Depends(connection)):
    return PostgresStudentRepository(db)

def courses(db=Depends(connection)):
    return PostgresCourseRepository(db)

def enrollments(db=Depends(connection)):
    return PostgresEnrollmentRepository(db)

class LocalEnrollmentPolicy:
    def is_allowed(self, student, course):
        return True

def policy():
    url = os.getenv("ENROLLMENT_POLICY_URL", "")
    return HttpEnrollmentPolicy(url, timeout=2) if url else LocalEnrollmentPolicy()

def service(c=Depends(students), p=Depends(courses), s=Depends(enrollments), external=Depends(policy)):
    return EnrollmentService(c, p, s, external)

def error(status, code, message):
    return JSONResponse(status_code=status, content={"code": code, "message": message})


@app.exception_handler(ResourceNotFoundError)
async def missing_handler(request, exception):
    return error(404, "NOT_FOUND", str(exception))


@app.exception_handler(EnrollmentRejectedError)
async def conflict_handler(request, exception):
    return error(409, "ENROLLMENT_CONFLICT", str(exception))


@app.exception_handler(EnrollmentPolicyError)
async def policy_handler(request, exception):
    return error(502, "POLICY_UNAVAILABLE", "Зовнішня перевірка недоступна")


@app.exception_handler(ValueError)
@app.exception_handler(RequestValidationError)
async def input_handler(request, exception):
    return error(400, "INVALID_REQUEST", "Некоректні дані запиту")


@app.exception_handler(psycopg.Error)
async def database_handler(request, exception):
    if exception.sqlstate == "23505":
        return error(409, "DUPLICATE_RESOURCE", "Ресурс із таким кодом уже існує")
    if exception.sqlstate == "23503":
        return error(409, "RESOURCE_CONFLICT", "Конфлікт пов’язаних даних")
    if exception.sqlstate in ("23514", "23502", "22003"):
        return error(400, "INVALID_REQUEST", "Некоректні дані ресурсу")
    return error(500, "DATABASE_ERROR", "Помилка роботи з БД")


@app.exception_handler(HTTPException)
async def http_handler(request, exception):
    response = error(exception.status_code,
                     "NOT_FOUND" if exception.status_code == 404 else "METHOD_NOT_ALLOWED",
                     "Маршрут не знайдено" if exception.status_code == 404 else "Метод не підтримується")
    if exception.headers:
        response.headers.update(exception.headers)
    return response


@app.post("/students", status_code=201)
def create_students(body: StudentInput, response: Response, repository=Depends(students)):
    saved = repository.create(Student(id=0, **body.model_dump()))
    response.headers["Location"] = f"/students/{saved.id}"
    return saved

@app.get("/students/{resource_id}")
def get_students(resource_id: int, repository=Depends(students)):
    saved = repository.find_by_id(resource_id)
    if saved is None:
        raise ResourceNotFoundError("Слухача не знайдено")
    return saved

@app.post("/courses", status_code=201)
def create_courses(body: CourseInput, response: Response, repository=Depends(courses)):
    saved = repository.create(Course(id=0, **body.model_dump()))
    response.headers["Location"] = f"/courses/{saved.id}"
    return saved

@app.get("/courses/{resource_id}")
def get_courses(resource_id: int, repository=Depends(courses)):
    saved = repository.find_by_id(resource_id)
    if saved is None:
        raise ResourceNotFoundError("Курс не знайдено")
    return saved

@app.post("/enrollments", status_code=201)
def create_enrollment(body: EnrollmentInput, response: Response, enrollment_service=Depends(service)):
    saved = enrollment_service.enroll(EnrollmentRequest(**body.model_dump()))
    response.headers["Location"] = f"/enrollments/{saved.id}"
    return saved

@app.get("/enrollments/{enrollment_id}")
def get_enrollment(enrollment_id: int, repository=Depends(enrollments)):
    saved = repository.find_by_id(enrollment_id)
    if saved is None:
        raise ResourceNotFoundError("Заявку не знайдено")
    return saved

@app.post("/enrollments/{enrollment_id}/complete")
def complete(enrollment_id: int, enrollment_service=Depends(service)):
    return enrollment_service.complete(enrollment_id)

@app.post("/enrollments/{enrollment_id}/cancel")
def cancel(enrollment_id: int, body: CancelInput, enrollment_service=Depends(service)):
    return enrollment_service.cancel(enrollment_id, body.days_before_start)


_ui_directory = Path(__file__).resolve().parents[2] / "sut-c-ui"
if _ui_directory.is_dir():
    app.mount("/", StaticFiles(directory=str(_ui_directory), html=True), name="ui")
