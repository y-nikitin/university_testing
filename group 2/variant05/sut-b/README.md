# SUT B — Доставка посилок, варіант 05

Java 21, Maven, PostgreSQL 17, JDBC, JUnit 5 та Mockito.

## Спільні правила

Клієнт має унікальний непорожній code до 40 символів та ознаку regular. Посилка має окремий унікальний непорожній code до 40 символів, вагу 1–30 000 г, оголошену вартість 0–100 000 000 копійок та ознаку fragile. Відстань — 1–5000 км. Відправлення містить одну посилку для одного клієнта. reference унікальний, непорожній, до 40 символів. Ідентифікатори заявки додатні; вага й відстань — цілі числа.

Базовий тариф — 50 грн + 10 грн за кожен розпочатий кілограм + 0,10 грн за кілометр. При 1000 г і 100 км — 70 грн, при 1001 г і 100 км — 80 грн. Межі тарифу — 6010–85000 копійок.

createShipment перевіряє заявку, існування клієнта й посилки, параметри та персональні обмеження. Потім один раз викликає ShippingPolicy.isAllowed(customer, parcel, distanceKm). False — ShipmentRejectedException; виняток залежності передається виклику. При цих відмовах запису немає.

Успіх створює Registered зі знімком ваги, оголошеної вартості, fragile, regular, couponValid та сум. Зміни карток не змінюють відправлення. baseFee — тариф, adjustment — доплата або від’ємна знижка, total = baseFee + adjustment. Початковий refund — 0.

Дозволені переходи: Registered → Dispatched → Delivered та Registered → Cancelled. Скасування після відправлення заборонене; при скасуванні Registered повертається весь total. Повторні переходи, доставка до відправлення та скасування Delivered заборонені. Зміна статусу не викликає зовнішнього дозволу. Скасування зберігає refund за старим total; інші суми незмінні. У навчальній моделі total вважається сплаченим, реальної оплати немає.

pickup — отримання у пункті видачі в місці призначення. Кілька відправлень можуть посилатися на одну картку посилки з різними reference. Реальні платежі, календар, маршрути та одночасна обробка не моделюються.

## Персональне правило — доплата за крихкість

Для некрихких посилок доплати немає. Для крихких: 20 грн до 5000 г включно, 50 грн понад 5000 г.

Усі пороги включні, крім явно зазначеного «понад». Гроші — цілі копійки. Відсоткова сума округлюється донизу один раз після розрахунку. Решта ознак, не згаданих у правилі, на ціну або дозвіл не впливає. Інших знижок чи доплат немає.

## API

`ShippingService(customers, parcels, shipments, policy)`:

- `calculateBaseFee(weightGrams, distanceKm)` — тариф;
- `calculateFragileSurcharge(weightGrams, fragile)` — персональна функція;
- `calculateRefund(total, status)` — повернення за поточним статусом; total — 0–3 085 000 копійок;
- `createShipment(new ShipmentRequest(reference, customerId, parcelId, distanceKm, urgent, pickup, couponValid))` — оформлення;
- `dispatch(id)`, `deliver(id)`, `cancel(id)` — зміни статусів.

Customer(id, code, regular), Parcel(id, code, weightGrams, declaredValue, fragile) — дані клієнта й посилки. Некоректні входи та відсутні записи — IllegalArgumentException; бізнес-відмови — ShipmentRejectedException. Помилки БД передаються як SQLException.

CustomerRepository і ParcelRepository: create(model), findById(id), update(model), delete(id). ShipmentRepository: create(shipment), findById(id), findShipments(customerId, status), updateStatus(id, expected, next, refund), delete(id). Репозиторії можна підмінити Mockito або підкласами; ShippingPolicy — Stub/Mock чи лямбдою.

create повертає модель з id БД, ігноруючи вхідний id. findById повертає null за відсутності запису. findShipments застосовує обидва фільтри одночасно та сортує за id. update відсутнього клієнта/посилки — IllegalArgumentException. updateStatus перевіряє допустимість переходу та поточний статус: заборонена пара — IllegalArgumentException, відсутність відповідного запису — ShipmentRejectedException. Видалення відсутнього запису нічого не змінює. Клієнта або посилку з відправленнями видалити не можна.

Репозиторії отримують відкрите JDBC-з’єднання; власник закриває його через try-with-resources. Оформлення записує один рядок одним атомарним INSERT. Використовуйте autoCommit=true та не використовуйте з’єднання паралельно. Реальної реалізації зовнішнього дозволу немає.

## Підготовка та запуск

У локальній PostgreSQL створіть користувача (один раз) та окрему БД:

```sql
CREATE ROLE delivery_student LOGIN;
\password delivery_student
CREATE DATABASE delivery_variant05_test OWNER delivery_student;
```

Із папки sut-b застосуйте міграцію один раз до порожньої БД:

```sh
psql -h localhost -U delivery_student -d delivery_variant05_test -v ON_ERROR_STOP=1 -f migrations/V1__create_tables.sql
```

Задайте змінні середовища за .env.example. Файл .env автоматично не читається; пароль — DB_PASSWORD.

```sh
mvn compile
mvn test
```

Є лише тест підключення. Дані для сценаріїв готуйте у власних тестах. Схеми та дані автоматично не створюються й не очищаються. Для незалежності тестів видаляйте власні відправлення перед клієнтами та посилками або використовуйте окрему тестову схему.
