# SUT B — Доставка посилок, варіант 01

Java 21, Maven, PostgreSQL 17, JDBC, JUnit 5 та Mockito.

## Спільні правила

Клієнт має унікальний непорожній code до 40 символів та ознаку regular. Посилка має окремий унікальний непорожній code до 40 символів, вагу 1–30 000 г, оголошену вартість 0–100 000 000 копійок та ознаку fragile. Відстань — 1–5000 км. Відправлення містить одну посилку для одного клієнта. reference унікальний, непорожній, до 40 символів. Ідентифікатори заявки додатні; вага й відстань — цілі числа.

Базовий тариф визначається персональним правилом нижче; його межі — 6010–61000 копійок.

createShipment перевіряє заявку, існування клієнта й посилки, параметри та персональні обмеження. Потім один раз викликає ShippingPolicy.isAllowed(customer, parcel, distanceKm). False — ShipmentRejectedException; виняток залежності передається виклику. При цих відмовах запису немає.

Успіх створює Registered зі знімком ваги, оголошеної вартості, fragile, regular, couponValid та сум. Зміни карток не змінюють відправлення. baseFee — тариф, adjustment — доплата або від’ємна знижка, total = baseFee + adjustment. Початковий refund — 0.

Дозволені переходи: Registered → Dispatched → Delivered та Registered → Cancelled. Скасування після відправлення заборонене; при скасуванні Registered повертається весь total. Повторні переходи, доставка до відправлення та скасування Delivered заборонені. Зміна статусу не викликає зовнішнього дозволу. Скасування зберігає refund за старим total; інші суми незмінні. У навчальній моделі total вважається сплаченим, реальної оплати немає.

pickup — отримання у пункті видачі в місці призначення. Кілька відправлень можуть посилатися на одну картку посилки з різними reference. Реальні платежі, календар, маршрути та одночасна обробка не моделюються.

## Персональне правило — тариф за ваговими діапазонами

Вагова складова: 10 грн до 1000 г, 30 грн для 1001–5000 г, 60 грн для 5001–30000 г. Тариф — 50 грн + вагова складова + 0,10 грн за кілометр.

Усі пороги включні, крім явно зазначеного «понад». Гроші — цілі копійки. Відсоткова сума округлюється донизу один раз після розрахунку. Решта ознак, не згаданих у правилі, на ціну або дозвіл не впливає. Інших знижок чи доплат немає.

## API

`ShippingService(customers, parcels, shipments, policy)`:

- `calculateBaseFee(weightGrams, distanceKm)` — тариф;
- `calculateBaseFee(weightGrams, distanceKm)` — персональна функція;
- `calculateRefund(total, status)` — повернення за поточним статусом; total — 0–3 085 000 копійок;
- `createShipment(new ShipmentRequest(reference, customerId, parcelId, distanceKm, urgent, pickup, couponValid))` — оформлення;
- `dispatch(id)`, `deliver(id)`, `cancel(id)` — зміни статусів.

Customer(id, code, regular), Parcel(id, code, weightGrams, declaredValue, fragile) — дані клієнта й посилки. Некоректні входи та відсутні записи — IllegalArgumentException; бізнес-відмови — ShipmentRejectedException. Помилки БД передаються як SQLException.

CustomerRepository і ParcelRepository: create(model), findById(id), update(model), delete(id). ShipmentRepository: create(shipment), findById(id), findShipments(customerId, status), updateStatus(id, expected, next, refund), delete(id). Репозиторії можна підмінити Mockito або підкласами; ShippingPolicy — Stub/Mock чи лямбдою.

create повертає модель з id БД, ігноруючи вхідний id. findById повертає null за відсутності запису. findShipments застосовує обидва фільтри одночасно та сортує за id. update відсутнього клієнта/посилки — IllegalArgumentException. updateStatus перевіряє допустимість переходу та поточний статус: заборонена пара — IllegalArgumentException, відсутність відповідного запису — ShipmentRejectedException. Видалення відсутнього запису нічого не змінює. Клієнта або посилку з відправленнями видалити не можна.

Репозиторії отримують відкрите JDBC-з’єднання; власник закриває його через try-with-resources. Оформлення записує один рядок одним атомарним INSERT. Використовуйте autoCommit=true та не використовуйте з’єднання паралельно. Для зовнішнього дозволу доступна HTTP-реалізація HttpShippingPolicy.

## Підготовка та запуск

У локальній PostgreSQL створіть користувача (один раз) та окрему БД:

```sql
CREATE ROLE delivery_student LOGIN;
\password delivery_student
CREATE DATABASE delivery_variant01_test OWNER delivery_student;
```

Із папки sut-b застосуйте міграцію один раз до порожньої БД:

```sh
psql -h localhost -U delivery_student -d delivery_variant01_test -v ON_ERROR_STOP=1 -f migrations/V1__create_tables.sql
```

Задайте змінні середовища за .env.example. Файл .env автоматично не читається; пароль — DB_PASSWORD.

```sh
mvn compile
mvn test
```

Є лише тест підключення. Дані для сценаріїв готуйте у власних тестах. Схеми та дані автоматично не створюються й не очищаються. Для незалежності тестів видаляйте власні відправлення перед клієнтами та посилками або використовуйте окрему тестову схему.

## HTTP-політика доставки

`HttpShippingPolicy` реалізує `ShippingPolicy` через HTTP. Адреса й таймаут задаються конструктором:

```java
var policy = new HttpShippingPolicy(URI.create(wiremockUrl), Duration.ofSeconds(2));
var service = new ShippingService(customers, parcels, shipments, policy);
```

Потрібні імпорти `java.net.URI` та `java.time.Duration`. `wiremockUrl` — базова HTTP(S)-адреса WireMock без шляху, з фактичним портом контейнера.

Після локальних перевірок сервіс надсилає один `POST /shipping-policy` з `Content-Type: application/json`:

```json
{"customerId":1,"parcelId":2,"distanceKm":100}
```

HTTP 200 із `{"allowed":true}` дозволяє зберегти відправлення зі статусом `Registered` і розрахованими сумами. HTTP 200 із `{"allowed":false}` спричиняє `ShipmentRejectedException` без нового запису. Інший статус, некоректний JSON, відсутнє або нелогічне поле `allowed`, мережева помилка чи таймаут спричиняють `IllegalStateException`; відправлення також не створюється. Переривання потоку відновлює його ознаку переривання й спричиняє `IllegalStateException`. Перенаправлення вимкнені; адаптер не реалізує повторних спроб.

Локальна відмова до перевірки політики та зміна статусу відправлення не викликають HTTP-політику. Персональні правила залишаються чинними. У тестах перевіряйте фактичні записи PostgreSQL та кількість і тіло запитів у журналі WireMock.


## REST API

Запуск: `mvn spring-boot:run` або клас `DeliveryApplication` (Java 21).
Потрібна підготовлена PostgreSQL із застосованими міграціями цього варіанта.
Підключення задають `DB_HOST`, `DB_PORT`, `DB_NAME` (типово `delivery_variant01_test`), `DB_USER` (типово `delivery_student`), `DB_PASSWORD`.
Адреса API: `http://127.0.0.1:8080`. Порт можна змінити через `API_PORT`.
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
- Посилка: `{"code": "P-001", "weightGrams": 1000, "declaredValue": 10000, "fragile": false}`.
- Відправлення: `{"reference": "S-001", "customerId": 1, "parcelId": 1, "distanceKm": 100, "urgent": false, "pickup": false, "couponValid": false}`.

Підставляйте ідентифікатори з відповідей створення клієнта та посилки. Дії `dispatch`, `deliver`, `cancel` виконуються без тіла запиту.
Суми — у копійках; вага — у грамах; відстань — у кілометрах.
Персональне правило з попереднього опису залишається чинним: перевіряйте через GET розраховані суми та збережений статус після дії.
Статуси: `Registered`, `Dispatched`, `Delivered`, `Cancelled`. Недозволений перехід або бізнес-відмова повертає 409; відсутній ресурс — 404; некоректний запит — 400.
Помилки мають вигляд `{"code":"...","message":"..."}`.

`SHIPPING_POLICY_URL` підключає наявний HTTP-клієнт зовнішньої перевірки. Без цієї змінної локальна політика дозволяє операції; бізнес-обмеження сервісу продовжують діяти.
Недоступна зовнішня перевірка повертає 502. API не застосовує міграції та не очищає БД автоматично.
Spring керує залежностями й пулом з’єднань; репозиторії використовують JdbcTemplate. Конструктори з Connection залишені для попередніх тестів. Помилки SQL обгортаються в DataAccessException.
