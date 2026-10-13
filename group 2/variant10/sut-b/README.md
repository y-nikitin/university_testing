# SUT B — Доставка посилок, варіант 10

Python 3.12+, PostgreSQL 17, psycopg та pytest.

## Спільні правила

Клієнт має унікальний непорожній код до 40 символів та ознаку regular. Посилка має окремий унікальний непорожній код до 40 символів, вагу 1–30 000 грамів та оголошену вартість 0–100 000 000 копійок. Відстань — ціле число 1–5000 км. Одне відправлення містить одну посилку для одного клієнта. reference унікальний, непорожній, до 40 символів. Ідентифікатори заявок — додатні цілі до 2⁶³−1. bool не допускається замість чисел; логічні ознаки — лише bool.

Базовий тариф — 50 грн + 10 грн за кожен розпочатий кілограм + 0,10 грн за кілометр. При 1000 г і 100 км — 70 грн; при 1001 г і 100 км — 80 грн. Межі базового тарифу: 6010–85000 копійок.

create_shipment перевіряє заявку, існування клієнта й посилки, допустимість параметрів та персональні обмеження. Потім один раз викликає ShippingPolicy.is_allowed(customer, parcel, distance_km). False — ShipmentRejectedError, відповідь іншого типу — ValueError; виняток залежності передається виклику. При відмовах відправлення не записується.

Успіх створює Registered зі знімком ваги, оголошеної вартості, regular, coupon_valid та сум. Зміни клієнта або посилки не змінюють збережене відправлення. base_fee — тариф, adjustment — від’ємна знижка або 0, total = base_fee + adjustment.

Дозволені переходи: Registered → Dispatched → Delivered та Registered → Cancelled. Повторні переходи, доставка до відправлення та скасування після відправлення заборонені. Перехід змінює лише статус; суми зберігаються. Зовнішня залежність при зміні статусу не викликається.

pickup означає отримання у пункті видачі в місці призначення. Кілька відправлень можуть посилатися на одну картку посилки; reference у них різні. Реальні платежі, маршрути, календар і одночасна обробка не моделюються.

## Персональне правило — знижка за промокодом

За підтвердженим промокодом — знижка 15% базового тарифу, але не більше 100 грн. Без нього знижка нульова. coupon_valid — ознака вже підтвердженого промокоду; перевірка текстових кодів не входить до системи.

Усі пороги включні. Гроші — цілі копійки. Відсоткова сума округлюється донизу один раз, після чого застосовується максимум. Ознаки, не згадані в правилі, на ціну або дозвіл не впливають. Інших знижок чи доплат немає.

## API

`ShippingService(customers, parcels, shipments, policy)`:

- `calculate_base_fee(weight_grams, distance_km)` — тариф;
- `calculate_discount(base_fee, coupon_valid)` — невід’ємна сума знижки;
- `create_shipment(ShipmentRequest(reference, customer_id, parcel_id, distance_km, urgent=False, pickup=False, coupon_valid=False))` — оформлення;
- `dispatch(shipment_id)`, `deliver(shipment_id)`, `cancel(shipment_id)` — зміна статусу.

Некоректні входи та відсутні записи — ValueError; бізнес-відмови — ShipmentRejectedError. Помилки БД передаються виклику.

PostgresCustomerRepository та PostgresParcelRepository: create(model), find_by_id, update(model), delete. PostgresShipmentRepository: create(shipment), find_by_id, find_shipments(customer_id, status), update_status(shipment_id, expected, next_status), delete. Контракти залежностей для Stub, Mock, Spy та Fake — у delivery/ports.py.

create повертає модель з id БД, ігноруючи вхідний id. find_by_id повертає None за відсутності запису. find_shipments застосовує обидва фільтри одночасно та сортує за id. update відсутнього клієнта/посилки — ValueError. update_status перевіряє допустимість переходу та поточний статус: заборонена пара статусів — ValueError, відсутність відповідного запису — ShipmentRejectedError. Видалення відсутнього запису нічого не змінює. Клієнта або посилку з відправленнями видалити не можна; спочатку видаляються відправлення.

Репозиторії отримують відкрите з’єднання від delivery.database.connect з autocommit=True. Власник закриває його через with. Оформлення записує один рядок одним атомарним INSERT. Не використовуйте спільне з’єднання паралельно. Для ShippingPolicy передайте тестовий двійник; для HTTP-перевірки доступна реалізація HttpShippingPolicy.

## Підготовка та запуск

Із папки sut-b:

```sh
python -m venv .venv
```

Активуйте середовище: PowerShell — `.venv\Scripts\Activate.ps1`, Linux/macOS — `source .venv/bin/activate`.

```sh
python -m pip install -e ".[test]"
```

У локальній PostgreSQL створіть користувача (один раз) та окрему БД:

```sql
CREATE ROLE delivery_student LOGIN;
\password delivery_student
CREATE DATABASE delivery_variant10_test OWNER delivery_student;
```

Застосуйте міграцію один раз до порожньої БД:

```sh
psql -h localhost -U delivery_student -d delivery_variant10_test -v ON_ERROR_STOP=1 -f migrations/V1__create_tables.sql
```

Задайте змінні середовища за .env.example. Файл .env автоматично не читається; пароль задається через DB_PASSWORD.

```sh
python -m pytest
```

Є лише тест підключення. Дані для сценаріїв готуйте у власних тестах. Схеми та дані автоматично не створюються й не очищаються. Для незалежності тестів видаляйте власні відправлення перед клієнтами та посилками або використовуйте окрему тестову схему.

## HTTP-політика доставки

`HttpShippingPolicy` виконує зовнішню перевірку через HTTP. Додаткові пакети не потрібні.

```python
from delivery.http_shipping_policy import HttpShippingPolicy

policy = HttpShippingPolicy(wiremock_url, timeout=2)
service = ShippingService(customers, parcels, shipments, policy)
```

Адреса WireMock задається під час створення політики: базова HTTP(S)-адреса без шляху, з фактичним портом контейнера. Таймаут має бути додатним і скінченним. Таймаут задається в секундах для операцій сокета; з’єднання закривається після кожного виклику.

Після локальних перевірок сервіс надсилає один `POST /shipping-policy` з `Content-Type: application/json`:

```json
{"customerId":1,"parcelId":2,"distanceKm":100}
```

HTTP 200 із `{"allowed":true}` дозволяє зберегти відправлення зі статусом `Registered` і розрахованими сумами. HTTP 200 із `{"allowed":false}` спричиняє `ShipmentRejectedError` без нового запису. Інший HTTP-статус, некоректний JSON, відсутнє або нелогічне поле `allowed`, мережева помилка чи таймаут спричиняють `ShippingPolicyError`; відправлення також не створюється. Перенаправлення вимкнені; адаптер не реалізує повторних спроб.

Локальна відмова до перевірки політики не надсилає HTTP-запит. Зміна статусу відправлення також не звертається до HTTP-політики. Персональні правила залишаються чинними. У тестах перевіряйте фактичні записи PostgreSQL та кількість і тіло запитів у журналі WireMock.


## REST API

Запуск: `python -m pip install -e ".[test]"`, потім `python -m uvicorn delivery.api:app --host 127.0.0.1 --port 8080` (Python 3.12+). Опис API доступний на `/docs`.
Потрібна підготовлена PostgreSQL із застосованими міграціями цього варіанта.
Підключення задають `DB_HOST`, `DB_PORT`, `DB_NAME` (типово `delivery_variant10_test`), `DB_USER` (типово `delivery_student`), `DB_PASSWORD`.
Адреса API: `http://127.0.0.1:8080`. Порт задається аргументом `--port`.
Змінні середовища потрібно задати перед запуском; файл `.env.example` є лише зразком.

| Метод | Шлях | Результат |
|---|---|---|
| POST | `/customers` | Створений клієнт, 201 та Location |
| GET | `/customers/{id}` | Клієнт із БД, 200 |
| POST | `/parcels` | Створена посилка, 201 та Location |
| GET | `/parcels/{id}` | Посилка з БД, 200 |
| POST | `/shipments` | Створене через сервіс відправлення, 201 та Location |
| GET | `/shipments/{id}` | Збережене відправлення, 200 |
| POST | `/shipments/{id}/dispatch` | Відправлення передано в доставку, 200 |
| POST | `/shipments/{id}/deliver` | Доставку завершено, 200 |
| POST | `/shipments/{id}/cancel` | Відправлення скасовано, 200 |

Приклади JSON для POST (`Content-Type: application/json`):

- Клієнт: `{"code": "C-001", "regular": false}`.
- Посилка: `{"code": "P-001", "weight_grams": 1000, "declared_value": 10000}`.
- Відправлення: `{"reference": "S-001", "customer_id": 1, "parcel_id": 1, "distance_km": 100, "urgent": false, "pickup": false, "coupon_valid": false}`.

Підставляйте ідентифікатори з відповідей створення клієнта та посилки. Дії `dispatch`, `deliver`, `cancel` виконуються без тіла запиту.
Суми — у копійках; вага — у грамах; відстань — у кілометрах.
Персональне правило з попереднього опису залишається чинним: перевіряйте через GET розраховані суми та збережений статус після дії.
Статуси: `Registered`, `Dispatched`, `Delivered`, `Cancelled`. Недозволений перехід або бізнес-відмова повертає 409; відсутній ресурс — 404; некоректний запит — 400.
Помилки мають вигляд `{"code":"...","message":"..."}`.

`SHIPPING_POLICY_URL` підключає наявний HTTP-клієнт зовнішньої перевірки. Без цієї змінної локальна політика дозволяє операції; бізнес-обмеження сервісу продовжують діяти.
Недоступна зовнішня перевірка повертає 502. API не застосовує міграції та не очищає БД автоматично.
