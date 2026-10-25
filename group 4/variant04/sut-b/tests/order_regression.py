import os
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from uuid import uuid4

import psycopg
import pytest
from testcontainers.postgres import PostgresContainer
from store.models import Product, OrderRequest, OrderStatus as S
from store.repositories import PostgresProductRepository, PostgresOrderRepository
from store.service import OrderService, OrderRejectedError

order_id = 0

class Policy:
    def is_valid(self, product, quantity):
        return True

@pytest.fixture(scope="module")
def sut():
    with PostgresContainer("postgres:17.6-alpine") as postgres:
        options = dict(host=postgres.get_container_host_ip(), port=postgres.get_exposed_port(5432),
                       user=postgres.username, password=postgres.password, dbname=postgres.dbname, autocommit=True)
        with psycopg.connect(**options) as db:
            migrations = Path(__file__).resolve().parents[1] / "migrations"
            for migration in sorted(migrations.glob("V*__*.sql")):
                db.execute(migration.read_text(encoding="utf-8"))
            product = PostgresProductRepository(db)
            orders = PostgresOrderRepository(db)
            yield db, product, orders, OrderService(product, orders, Policy()), options

def item(sut):
    return sut[1].create(str(uuid4()), 10000, 5, False)

def request(product):
    return OrderRequest(str(uuid4()), product.id, 1, False, True)

def test_create_order(sut):
    global order_id
    saved = sut[3].place_order(request(item(sut)))
    order_id = saved.id
    assert saved.status == S.CONFIRMED
    assert sut[2].find_by_id(saved.id) == saved

def test_return_order(sut):
    returned = sut[3].return_order(order_id, 0)
    assert returned.status == S.RETURNED
    assert sut[2].find_by_id(order_id) == returned

def test_product_stock(sut):
    saved = sut[1].create("REGRESSION-PRODUCT", 10000, 5, False)
    assert sut[1].find_by_id(saved.id).stock == 5

def test_product_rate(sut):
    saved = sut[1].create("REGRESSION-PRODUCT", 15000, 3, False)
    assert sut[1].find_by_id(saved.id).unit_price == 15000

def test_order_after_policy_check(sut):
    class DelayedPolicy:
        def is_valid(self, product, quantity):
            time.sleep(float(os.getenv("REGRESSION_POLICY_DELAY_MS", "200")) / 1000)
            return True

    product = item(sut)
    data = request(product)
    def place_order():
        with psycopg.connect(**sut[4]) as db:
            service = OrderService(PostgresProductRepository(db), PostgresOrderRepository(db), DelayedPolicy())
            return service.place_order(data)

    with ThreadPoolExecutor(max_workers=1) as executor:
        operation = executor.submit(place_order)
        time.sleep(0.05)
        saved = sut[2].find_orders(product.id, S.CONFIRMED)
        assert len(saved) == 1
        assert saved[0].reference == data.reference
        assert saved[0].total == 10000

def test_order_selection(sut):
    product = item(sut)
    prefix = str(uuid4())[:8]
    references = [prefix + "-A", prefix + "-B"]
    fixture_order = list(reversed(references)) if os.getenv("REGRESSION_REVERSE_FIXTURE") == "true" else references
    for reference in fixture_order:
        sut[3].place_order(OrderRequest(reference, product.id, 1, False, True))
    actual = [row[0] for row in sut[0].execute(
        "SELECT r.reference FROM orders r JOIN order_items i ON i.order_id=r.id WHERE i.product_id=%s", (product.id,))]
    assert actual == references

def test_order_amount(sut):
    product = item(sut)
    saved = sut[3].place_order(request(product))
    stored = sut[2].find_by_id(saved.id)
    assert stored.total == 10000
    assert stored.item.product_id == product.id
    assert stored.item.quantity == 1
    assert stored.item.unit_price == 10000

def test_stock_rejection(sut):
    product = sut[1].create(str(uuid4()), 10000, 0, False)
    with pytest.raises(OrderRejectedError):
        sut[3].place_order(OrderRequest(str(uuid4()), product.id, 1, False, True))
    assert sut[2].find_orders(product.id, S.CONFIRMED) == []

def test_coupon_rejection(sut):
    class DeniedCoupon:
        def is_valid(self, product, quantity):
            return False

    product = sut[1].create(str(uuid4()), 200000, 5, False)
    service = OrderService(sut[1], sut[2], DeniedCoupon())
    saved = service.place_order(request(product))
    assert saved.coupon_valid is False
    assert saved.total == 200000
    assert sut[2].find_by_id(saved.id) == saved

def test_repeated_return(sut):
    saved = sut[3].place_order(request(item(sut)))
    returned = sut[3].return_order(saved.id, 0)
    with pytest.raises(OrderRejectedError):
        sut[3].return_order(saved.id, 0)
    assert sut[2].find_by_id(saved.id) == returned
