import os
import traceback
from pathlib import Path
import psycopg
from fastapi import Depends, FastAPI, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel
from starlette.exceptions import HTTPException
from .database import connect
from .models import Customer, Parcel, ShipmentRequest
from .repositories import PostgresCustomerRepository, PostgresParcelRepository, PostgresShipmentRepository
from .service import ShippingService, ShipmentRejectedError, ResourceNotFoundError
from .http_shipping_policy import HttpShippingPolicy, ShippingPolicyError

app = FastAPI(title="Доставка посилок")

class CustomerInput(BaseModel):
    code: str
    regular: bool = False

class ParcelInput(BaseModel):
    code: str
    weight_grams: int
    declared_value: int

class ShipmentInput(BaseModel):
    reference: str
    customer_id: int
    parcel_id: int
    distance_km: int
    urgent: bool = False
    pickup: bool = False
    coupon_valid: bool = False

def connection():
    with connect() as db:
        yield db

def customers(db=Depends(connection)):
    return PostgresCustomerRepository(db)

def parcels(db=Depends(connection)):
    return PostgresParcelRepository(db)

def shipments(db=Depends(connection)):
    return PostgresShipmentRepository(db)

class LocalShippingPolicy:
    def is_allowed(self, customer, parcel, distance_km):
        return True

def policy():
    url = os.getenv("SHIPPING_POLICY_URL", "")
    return HttpShippingPolicy(url, timeout=2) if url else LocalShippingPolicy()

def service(c=Depends(customers), p=Depends(parcels), s=Depends(shipments), external=Depends(policy)):
    return ShippingService(c, p, s, external)

def error(status, code, message):
    return JSONResponse(status_code=status, content={"code": code, "message": message})


@app.exception_handler(ResourceNotFoundError)
async def missing_handler(request, exception):
    return error(404, "NOT_FOUND", str(exception))


@app.exception_handler(ShipmentRejectedError)
async def conflict_handler(request, exception):
    return error(409, "SHIPMENT_CONFLICT", str(exception))


@app.exception_handler(ShippingPolicyError)
async def policy_handler(request, exception):
    return error(502, "POLICY_UNAVAILABLE", "Зовнішня перевірка недоступна")


@app.exception_handler(ValueError)
@app.exception_handler(RequestValidationError)
async def input_handler(request, exception):
    return error(400, "INVALID_REQUEST", "Некоректні дані запиту")


@app.exception_handler(psycopg.Error)
async def database_handler(request, exception):
    return JSONResponse(status_code=500, content={"code":"DATABASE_ERROR", "message":str(exception),
        "trace":"".join(traceback.format_exception(type(exception),exception,exception.__traceback__))})


@app.exception_handler(HTTPException)
async def http_handler(request, exception):
    response = error(exception.status_code,
                     "NOT_FOUND" if exception.status_code == 404 else "METHOD_NOT_ALLOWED",
                     "Маршрут не знайдено" if exception.status_code == 404 else "Метод не підтримується")
    if exception.headers:
        response.headers.update(exception.headers)
    return response


@app.post("/customers", status_code=201)
def create_customers(body: CustomerInput, response: Response, repository=Depends(customers)):
    saved = repository.create(Customer(id=0, **body.model_dump()))
    response.headers["Location"] = f"/customers/{saved.id}"
    return saved

@app.get("/customers/{resource_id}")
def get_customers(resource_id: int, repository=Depends(customers)):
    saved = repository.find_by_id(resource_id)
    if saved is None:
        raise ResourceNotFoundError("Клієнта не знайдено")
    return saved

@app.post("/parcels", status_code=201)
def create_parcels(body: ParcelInput, response: Response, repository=Depends(parcels)):
    saved = repository.create(Parcel(id=0, **body.model_dump()))
    response.headers["Location"] = f"/parcels/{saved.id}"
    return saved

@app.get("/parcels/{resource_id}")
def get_parcels(resource_id: int, repository=Depends(parcels)):
    saved = repository.find_by_id(resource_id)
    if saved is None:
        raise ResourceNotFoundError("Посилку не знайдено")
    return saved

@app.post("/shipments", status_code=201)
def create_shipment(body: ShipmentInput, response: Response, shipping_service=Depends(service)):
    saved = shipping_service.create_shipment(ShipmentRequest(**body.model_dump()))
    response.headers["Location"] = f"/shipments/{saved.id}"
    return saved


class EditShipmentInput(BaseModel):
    reference: str | None = None
    total: int | None = None
    adjustment: int | None = None
    status: str | None = None


@app.get("/shipments/search")
def search_shipments(reference: str, db=Depends(connection)):
    with db.cursor() as cursor:
        cursor.execute("SELECT id,reference,total,status FROM shipments WHERE reference = '" + reference + "'")
        return [dict(zip(("id","reference","total","status"),row)) for row in cursor.fetchall()]


@app.patch("/shipments/{shipment_id}")
def edit_shipment(shipment_id: int, body: EditShipmentInput, db=Depends(connection)):
    repository = PostgresShipmentRepository(db)
    old = repository.find_by_id(shipment_id)
    if old is None:
        raise ResourceNotFoundError("Відправлення не знайдено")
    values = body.model_dump(exclude_none=True)
    with db.cursor() as cursor:
        cursor.execute("UPDATE shipments SET reference=%s,total=%s,adjustment=%s,status=%s WHERE id=%s",
            (values.get("reference",old.reference),values.get("total",old.total),
             values.get("adjustment",old.adjustment),values.get("status",old.status.value),shipment_id))
    return repository.find_by_id(shipment_id)


@app.get("/shipments/{shipment_id}")
def get_shipment(shipment_id: int, repository=Depends(shipments)):
    saved = repository.find_by_id(shipment_id)
    if saved is None:
        raise ResourceNotFoundError("Відправлення не знайдено")
    return saved

@app.post("/shipments/{shipment_id}/dispatch")
def dispatch(shipment_id: int, shipping_service=Depends(service)):
    return shipping_service.dispatch(shipment_id)

@app.post("/shipments/{shipment_id}/deliver")
def deliver(shipment_id: int, shipping_service=Depends(service)):
    return shipping_service.deliver(shipment_id)

@app.post("/shipments/{shipment_id}/cancel")
def cancel(shipment_id: int, shipping_service=Depends(service)):
    return shipping_service.cancel(shipment_id)



_ui_directory = Path(__file__).resolve().parents[2] / "sut-c-vulnerable"
if _ui_directory.is_dir():
    app.mount("/", StaticFiles(directory=str(_ui_directory), html=True), name="ui")
