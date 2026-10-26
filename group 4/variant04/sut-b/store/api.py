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
from .models import OrderRequest
from .repositories import PostgresProductRepository, PostgresOrderRepository
from .service import OrderService, OrderRejectedError, ResourceNotFoundError
from .http_coupon_validator import HttpCouponValidator, CouponValidationError

app = FastAPI(title="Інтернет-магазин — варіант 04")


class ProductInput(BaseModel):
    sku: str
    unit_price: int
    stock: int
    on_sale: bool = False


class OrderInput(BaseModel):
    reference: str
    product_id: int
    quantity: int
    premium: bool = False
    coupon_claimed: bool = False


class ReturnInput(BaseModel):
    days_since_purchase: int
    packaging_open: bool = False


def connection():
    with connect() as db:
        yield db


def products(db=Depends(connection)):
    return PostgresProductRepository(db)


def orders(db=Depends(connection)):
    return PostgresOrderRepository(db)


class LocalCouponValidator:
    def is_valid(self, product, quantity):
        return True


def coupons():
    url = os.getenv("COUPON_VALIDATOR_URL", "")
    return HttpCouponValidator(url, timeout=2) if url else LocalCouponValidator()


def service(product_repository=Depends(products), order_repository=Depends(orders), validator=Depends(coupons)):
    return OrderService(product_repository, order_repository, validator)


def error(status, code, message):
    return JSONResponse(status_code=status, content={"code": code, "message": message})


@app.exception_handler(ResourceNotFoundError)
async def missing_handler(request, exception):
    return error(404, "NOT_FOUND", str(exception))


@app.exception_handler(OrderRejectedError)
async def conflict_handler(request, exception):
    return error(409, "ORDER_CONFLICT", str(exception))


@app.exception_handler(CouponValidationError)
async def policy_handler(request, exception):
    return error(502, "COUPON_UNAVAILABLE", "Зовнішня перевірка недоступна")


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


@app.post("/products", status_code=201)
def create_product(body: ProductInput, response: Response, repository=Depends(products)):
    product = repository.create(body.sku, body.unit_price, body.stock, body.on_sale)
    response.headers["Location"] = f"/products/{product.id}"
    return product


@app.get("/products/{product_id}")
def get_product(product_id: int, repository=Depends(products)):
    product = repository.find_by_id(product_id)
    if product is None:
        raise ResourceNotFoundError("Товар не знайдено")
    return product


@app.post("/orders", status_code=201)
def create_order(body: OrderInput, response: Response, order_service=Depends(service)):
    order = order_service.place_order(OrderRequest(**body.model_dump()))
    response.headers["Location"] = f"/orders/{order.id}"
    return order


@app.get("/orders/{order_id}")
def get_order(order_id: int, repository=Depends(orders)):
    order = repository.find_by_id(order_id)
    if order is None:
        raise ResourceNotFoundError("Замовлення не знайдено")
    return order


@app.post("/orders/{order_id}/return")
def return_order(order_id: int, body: ReturnInput, order_service=Depends(service)):
    return order_service.return_order(order_id, body.days_since_purchase, body.packaging_open)


_ui_directory = Path(__file__).resolve().parents[2] / "sut-c-ui"
if _ui_directory.is_dir():
    app.mount("/", StaticFiles(directory=str(_ui_directory), html=True), name="ui")
