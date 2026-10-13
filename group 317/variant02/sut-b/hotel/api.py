import os
from datetime import date

import psycopg
from fastapi import Depends, FastAPI, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from starlette.exceptions import HTTPException

from .database import connect
from .models import Room, BookingRequest
from .repositories import PostgresRoomRepository, PostgresBookingRepository
from .service import BookingService, BookingRejectedError, ResourceNotFoundError
from .http_booking_policy import HttpBookingPolicy, BookingPolicyError

app = FastAPI(title="Бронювання готелю")


class RoomInput(BaseModel):
    code: str
    nightly_rate: int
    capacity: int


class BookingInput(BaseModel):
    reference: str
    room_id: int
    check_in: date
    check_out: date
    guests: int
    regular: bool = False


class CancellationInput(BaseModel):
    days_before: int


def connection():
    with connect() as db:
        yield db


def rooms(db=Depends(connection)):
    return PostgresRoomRepository(db)


def bookings(db=Depends(connection)):
    return PostgresBookingRepository(db)


class LocalBookingPolicy:
    def is_allowed(self, room, guests, nights):
        return True


def policy():
    url = os.getenv("BOOKING_POLICY_URL", "")
    return HttpBookingPolicy(url, timeout=2) if url else LocalBookingPolicy()


def service(room_repository=Depends(rooms), booking_repository=Depends(bookings), booking_policy=Depends(policy)):
    return BookingService(room_repository, booking_repository, booking_policy)


def error(status, code, message):
    return JSONResponse(status_code=status, content={"code": code, "message": message})


@app.exception_handler(ResourceNotFoundError)
async def missing_handler(request, exception):
    return error(404, "NOT_FOUND", str(exception))


@app.exception_handler(BookingRejectedError)
async def conflict_handler(request, exception):
    return error(409, "BOOKING_CONFLICT", str(exception))


@app.exception_handler(BookingPolicyError)
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


@app.post("/rooms", status_code=201)
def create_room(body: RoomInput, response: Response, repository=Depends(rooms)):
    room = repository.create(Room(0, body.code, body.nightly_rate, body.capacity))
    response.headers["Location"] = f"/rooms/{room.id}"
    return room


@app.get("/rooms/{room_id}")
def get_room(room_id: int, repository=Depends(rooms)):
    room = repository.find_by_id(room_id)
    if room is None:
        raise ResourceNotFoundError("Номер не знайдено")
    return room


@app.post("/bookings", status_code=201)
def create_booking(body: BookingInput, response: Response, booking_service=Depends(service)):
    booking = booking_service.book(BookingRequest(**body.model_dump()))
    response.headers["Location"] = f"/bookings/{booking.id}"
    return booking


@app.get("/bookings/{booking_id}")
def get_booking(booking_id: int, repository=Depends(bookings)):
    booking = repository.find_by_id(booking_id)
    if booking is None:
        raise ResourceNotFoundError("Бронювання не знайдено")
    return booking


@app.post("/bookings/{booking_id}/cancel")
def cancel_booking(booking_id: int, body: CancellationInput, booking_service=Depends(service)):
    return booking_service.cancel_booking(booking_id, body.days_before)
