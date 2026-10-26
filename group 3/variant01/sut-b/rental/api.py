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
from .models import Equipment, RentalRequest
from .repositories import PostgresEquipmentRepository, PostgresRentalRepository
from .service import RentalService, RentalRejectedError, ResourceNotFoundError
from .http_rental_policy import HttpRentalPolicy, RentalPolicyError

app = FastAPI(title="Прокат обладнання")


class EquipmentInput(BaseModel):
    code: str
    daily_rate: int
    stock: int


class RentalInput(BaseModel):
    reference: str
    equipment_id: int
    quantity: int
    days: int
    premium: bool = False


class ReturnInput(BaseModel):
    overdue_days: int


def connection():
    with connect() as db:
        yield db


def equipment(db=Depends(connection)):
    return PostgresEquipmentRepository(db)


def rentals(db=Depends(connection)):
    return PostgresRentalRepository(db)


class LocalRentalPolicy:
    def is_allowed(self, equipment, quantity, days):
        return True


def policy():
    url = os.getenv("RENTAL_POLICY_URL", "")
    return HttpRentalPolicy(url, timeout=2) if url else LocalRentalPolicy()


def service(equipment_repository=Depends(equipment), rental_repository=Depends(rentals), rental_policy=Depends(policy)):
    return RentalService(equipment_repository, rental_repository, rental_policy)


def error(status, code, message):
    return JSONResponse(status_code=status, content={"code": code, "message": message})


@app.exception_handler(ResourceNotFoundError)
async def missing_handler(request, exception):
    return error(404, "NOT_FOUND", str(exception))


@app.exception_handler(RentalRejectedError)
async def conflict_handler(request, exception):
    return error(409, "RENTAL_CONFLICT", str(exception))


@app.exception_handler(RentalPolicyError)
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


@app.post("/equipment", status_code=201)
def create_equipment(body: EquipmentInput, response: Response, repository=Depends(equipment)):
    saved = repository.create(Equipment(0, body.code, body.daily_rate, body.stock))
    response.headers["Location"] = f"/equipment/{saved.id}"
    return saved


@app.get("/equipment/{equipment_id}")
def get_equipment(equipment_id: int, repository=Depends(equipment)):
    saved = repository.find_by_id(equipment_id)
    if saved is None:
        raise ResourceNotFoundError("Обладнання не знайдено")
    return saved


@app.post("/rentals", status_code=201)
def create_rental(body: RentalInput, response: Response, rental_service=Depends(service)):
    saved = rental_service.rent(RentalRequest(**body.model_dump()))
    response.headers["Location"] = f"/rentals/{saved.id}"
    return saved


@app.get("/rentals/{rental_id}")
def get_rental(rental_id: int, repository=Depends(rentals)):
    saved = repository.find_by_id(rental_id)
    if saved is None:
        raise ResourceNotFoundError("Оренду не знайдено")
    return saved


@app.post("/rentals/{rental_id}/return")
def return_rental(rental_id: int, body: ReturnInput, rental_service=Depends(service)):
    return rental_service.return_rental(rental_id, body.overdue_days)


_ui_directory = Path(__file__).resolve().parents[2] / "sut-c-ui"
if _ui_directory.is_dir():
    app.mount("/", StaticFiles(directory=str(_ui_directory), html=True), name="ui")
