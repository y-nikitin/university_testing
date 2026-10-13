# API-тести: RestAssured і Postman

## 1. Тестовий клас

`src/test/java/hotel/BookingApiIT.java` — Spring Boot на випадковому порту, реальні сервіс і JDBC-репозиторії, PostgreSQL через Testcontainers. Flyway застосовує наявні міграції. Зовнішній дозвіл локальний і завжди позитивний; WireMock для цих сценаріїв не потрібен.

Запустіть Docker із Linux-контейнерами, потім із папки проєкту:

```sh
mvn -Dtest=BookingApiIT test
```

Локальна PostgreSQL і окремий запуск API для цього класу не потрібні. Клас містить 7 звичайних тестів і 3 запуски параметризованого тесту — разом 10. Усі бізнес-перевірки виконуються через HTTP. Кожен сценарій створює власні ресурси; дані інших сценаріїв не використовуються. БД видаляється разом із контейнером після класу, а не після кожного методу. Звичайний `mvn test` цей клас із суфіксом IT не запускає.

## 2. Запуск API для Postman

Для ручної роботи з Postman використовуйте окрему локальну тестову PostgreSQL. Створення БД, застосування міграцій та змінні DB_URL, DB_USER, DB_PASSWORD описано в основному README цього проєкту.

Не задавайте BOOKING_POLICY_URL для цієї демонстрації: зовнішній дозвіл буде позитивним. Якщо змінна залишилася від попередньої роботи, приберіть її з поточного PowerShell:

```powershell
Remove-Item Env:BOOKING_POLICY_URL -ErrorAction SilentlyContinue
mvn spring-boot:run
```

Залиште процес запущеним. За замовчуванням адреса API — `http://127.0.0.1:8080`. Postman і Newman не запускають API або БД самостійно. Авторизацію оберіть No Auth.

## 3. Колекція та змінні

Створіть колекцію «Готель — API». У її Variables додайте `baseUrl` зі значенням `http://127.0.0.1:8080`.

Створіть папку «Цикл бронювання». Додайте наведені нижче запити в зазначеному порядку. Скрипти перевірок вставляйте в **Scripts → Post-response** (у старіших версіях — Tests). Натискання Send виконує один запит; результати перевірок показано в Test Results.

Ця папка — один сценарій із залежними кроками. Запускайте її від початку. Для іншого незалежного сценарію створюйте власні номер і бронювання, а не використовуйте id із попередньої папки.

## 4. Створення номера

`POST {{baseUrl}}/rooms`

У Scripts → Pre-request:

```js
pm.collectionVariables.unset("roomId");
pm.collectionVariables.unset("bookingId");
pm.collectionVariables.unset("cancelledBooking");
pm.collectionVariables.set("roomNumber", pm.variables.replaceIn("{{$guid}}").slice(0, 8));
pm.collectionVariables.set("reference", pm.variables.replaceIn("{{$guid}}"));
```

Body → raw → JSON:

```json
{"number":"{{roomNumber}}","capacity":2,"nightlyPrice":120000}
```

Post-response:

```js
pm.test("Номер створено", () => pm.response.to.have.status(201));
const room = pm.response.json();
pm.test("Дані номера правильні", () => {
    pm.expect(room.id).to.be.a("number").above(0);
    pm.expect(room.number).to.eql(pm.collectionVariables.get("roomNumber"));
    pm.expect(room.capacity).to.eql(2);
    pm.expect(room.nightlyPrice).to.eql(120000);
    pm.expect(pm.response.headers.get("Location")).to.eql(`/rooms/${room.id}`);
});
if (pm.response.code === 201) pm.collectionVariables.set("roomId", room.id);
```

## 5. Читання номера

`GET {{baseUrl}}/rooms/{{roomId}}`

Post-response:

```js
pm.test("Номер доступний", () => pm.response.to.have.status(200));
pm.test("Прочитано створений номер", () => {
    const room = pm.response.json();
    pm.expect(room.id).to.eql(Number(pm.collectionVariables.get("roomId")));
    pm.expect(room.number).to.eql(pm.collectionVariables.get("roomNumber"));
    pm.expect(room.nightlyPrice).to.eql(120000);
});
```

## 6. Створення бронювання

`POST {{baseUrl}}/bookings`

Body → raw → JSON:

```json
{"reference":"{{reference}}","roomId":{{roomId}},"nights":7,"guests":2}
```

Post-response:

```js
pm.test("Бронювання створено", () => pm.response.to.have.status(201));
const booking = pm.response.json();
pm.test("Збережено правильні дані та знижку", () => {
    pm.expect(booking.id).to.be.a("number").above(0);
    pm.expect(booking.reference).to.eql(pm.collectionVariables.get("reference"));
    pm.expect(booking.roomId).to.eql(Number(pm.collectionVariables.get("roomId")));
    pm.expect(booking.nights).to.eql(7);
    pm.expect(booking.guests).to.eql(2);
    pm.expect(booking.status).to.eql("CONFIRMED");
    pm.expect(booking.total).to.eql(798000);
});
if (pm.response.code === 201) pm.collectionVariables.set("bookingId", booking.id);
```

## 7. Читання бронювання

`GET {{baseUrl}}/bookings/{{bookingId}}`

Post-response:

```js
pm.test("Бронювання доступне", () => pm.response.to.have.status(200));
pm.test("Збережені дані правильні", () => {
    const booking = pm.response.json();
    pm.expect(booking.id).to.eql(Number(pm.collectionVariables.get("bookingId")));
    pm.expect(booking.reference).to.eql(pm.collectionVariables.get("reference"));
    pm.expect(booking.roomId).to.eql(Number(pm.collectionVariables.get("roomId")));
    pm.expect(booking.nights).to.eql(7);
    pm.expect(booking.guests).to.eql(2);
    pm.expect(booking.total).to.eql(798000);
    pm.expect(booking.status).to.eql("CONFIRMED");
});
```

## 8. Скасування

`POST {{baseUrl}}/bookings/{{bookingId}}/cancel`, Body → none.

Post-response:

```js
pm.test("Скасування успішне", () => pm.response.to.have.status(200));
pm.test("Статус змінено, сума збережена", () => {
    const booking = pm.response.json();
    pm.expect(booking.status).to.eql("CANCELLED");
    pm.expect(booking.total).to.eql(798000);
    pm.expect(booking.id).to.eql(Number(pm.collectionVariables.get("bookingId")));
});
```

Наступний запит: `GET {{baseUrl}}/bookings/{{bookingId}}`.

```js
pm.test("Скасування збережене", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().status).to.eql("CANCELLED");
    pm.expect(pm.response.json().total).to.eql(798000);
});
if (pm.response.code === 200) {
    pm.collectionVariables.set("cancelledBooking", JSON.stringify(pm.response.json()));
}
```

## 9. Повторне скасування та незмінність

Ще раз `POST {{baseUrl}}/bookings/{{bookingId}}/cancel`, Body → none.

```js
pm.test("Повторне скасування відхилено", () => pm.response.to.have.status(409));
pm.test("Контракт помилки", () => {
    const error = pm.response.json();
    pm.expect(error.code).to.eql("BOOKING_CONFLICT");
    pm.expect(error.message).to.be.a("string").and.not.empty;
});
```

Після нього `GET {{baseUrl}}/bookings/{{bookingId}}`:

```js
pm.test("Після відмови дані незмінні", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json()).to.deep.eql(
        JSON.parse(pm.collectionVariables.get("cancelledBooking"))
    );
});
```

## 10. Неіснуючий ресурс

Окрема папка «Неіснуюче бронювання», запит:

`GET {{baseUrl}}/bookings/9223372036854775807`

Для нової навчальної БД цей id не створюється. Передавайте його текстом у URL, не перетворюйте на JavaScript Number.

```js
pm.test("Бронювання не знайдено", () => pm.response.to.have.status(404));
pm.test("Контракт помилки", () => {
    const error = pm.response.json();
    pm.expect(error.code).to.eql("NOT_FOUND");
    pm.expect(error.message).to.be.a("string").and.not.empty;
});
```

## 11. Параметризована перевірка знижки

Створіть окрему колекцію «Готель — знижка» з baseUrl і трьома запитами: створення номера, створення бронювання, читання бронювання. Скопіюйте підготовку номера з пункту 4. Кожна ітерація створює свої дані.

Створіть файл `discount-data.json`:

```json
[
  {"nights":6,"expectedTotal":720000},
  {"nights":7,"expectedTotal":798000},
  {"nights":8,"expectedTotal":912000}
]
```

У POST бронювання змініть тіло:

```json
{"reference":"{{reference}}","roomId":{{roomId}},"nights":{{nights}},"guests":2}
```

Замість скрипту з фіксованими 7 ночами використайте:

```js
pm.test("Бронювання створено", () => pm.response.to.have.status(201));
pm.test("Сума відповідає набору даних", () => {
    const booking = pm.response.json();
    pm.expect(booking.nights).to.eql(Number(pm.iterationData.get("nights")));
    pm.expect(booking.total).to.eql(Number(pm.iterationData.get("expectedTotal")));
    pm.expect(booking.status).to.eql("CONFIRMED");
});
if (pm.response.code === 201) pm.collectionVariables.set("bookingId", pm.response.json().id);
```

У GET бронювання:

```js
pm.test("Розрахунок збережено", () => {
    pm.response.to.have.status(200);
    pm.expect(pm.response.json().id).to.eql(Number(pm.collectionVariables.get("bookingId")));
    pm.expect(pm.response.json().total).to.eql(Number(pm.iterationData.get("expectedTotal")));
});
```

Відкрийте Run колекції, виберіть файл даних і виконайте три ітерації. Не запускайте цю колекцію без файлу даних.

## 12. Запуск і здавання

Основну колекцію запускайте через Run повністю, одну ітерацію. Усередині workflow порядок важливий; різні сценарії не повинні використовувати ресурси один одного.

Експортуйте обидві колекції у форматі Collection v2.1: `hotel-api.postman_collection.json` і `hotel-discount.postman_collection.json`. Збережіть їх разом із `discount-data.json`. Перед здаванням видаліть з експортованих змінних старі roomId, bookingId і знімки відповідей — їх установлюють скрипти під час запуску.

Установіть Newman:

```sh
npm install -g newman
```

Запуск:

```sh
newman run hotel-api.postman_collection.json --env-var baseUrl=http://127.0.0.1:8080 --bail
newman run hotel-discount.postman_collection.json --env-var baseUrl=http://127.0.0.1:8080 -d discount-data.json --bail
```

Щоб запускати обидва набори однією командою, створіть `run-api-tests.ps1`:

```powershell
newman run "$PSScriptRoot/hotel-api.postman_collection.json" --env-var baseUrl=http://127.0.0.1:8080 --bail
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
newman run "$PSScriptRoot/hotel-discount.postman_collection.json" --env-var baseUrl=http://127.0.0.1:8080 -d "$PSScriptRoot/discount-data.json" --bail
exit $LASTEXITCODE
```

Запуск: `./run-api-tests.ps1`. Невдала перевірка має давати ненульовий код завершення. Після завершення Newman дані локальної БД залишаються: API не має DELETE. Використовуйте окрему тестову БД, унікальні коди й очищення або перевідтворення БД між повними навчальними запусками за потреби. Паролі БД у Postman не передаються.

Кількість pm.test — це кількість перевірок, а не самостійних бізнес-сценаріїв. Кілька запитів одного workflow утворюють один сценарій. Інструкція демонструє базову роботу; для повного покриття додайте незалежні сценарії зі списку лабораторної, наприклад бізнес-відмову при перевищенні місткості.

Офіційні довідки: [скрипти перевірок Postman](https://learning.postman.com/docs/tests-and-scripts/write-scripts/test-scripts/), [Newman](https://github.com/postmanlabs/newman).

## Той самий клас без контейнерів

`BookingApiLocalIT` містить ті самі тестові методи, що й `BookingApiIT`. `@SpringBootTest(RANDOM_PORT)` автоматично запускає Spring Boot на вільному порту, а `@LocalServerPort` передає порт запитам RestAssured. Після класу Spring-контекст закривається. Docker та окремий запуск API не потрібні.

1. Запустіть окрему локальну тестову PostgreSQL та застосуйте міграції за README.
2. Налаштуйте підключення через DB_URL, DB_USER, DB_PASSWORD або database-test.properties.
3. Запустіть клас кнопкою в IDE або з папки проєкту:

```sh
mvn -Dtest=BookingApiLocalIT test
```

Клас використовує позитивний локальний дозвіл без WireMock. Міграції автоматично не виконує. Тести створюють власні дані з унікальними кодами. Після завершення записи залишаються в локальній БД; автоматичного очищення немає. Використовуйте лише навчальну тестову БД.
