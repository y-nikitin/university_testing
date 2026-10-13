# SUT B — Реєстрація на навчальні курси, варіант 18

Java 21, Maven, PostgreSQL 17, JDBC, JUnit 5 та Mockito.

## Спільні правила

Слухач має унікальний непорожній code до 40 символів, вік 14–100, вступний бал 0–100 та ознаку regular. Курс має окремий унікальний непорожній code до 40 символів, ціну 100–10 000 000 копійок, місткість 1–100 і рівень 1–3. Заявка містить одного слухача й один курс. reference унікальний, непорожній, до 40 символів; ідентифікатори додатні.

Зарахування перевіряє заявку, існування карток, відсутність нескасованої заявки, вільне місце та персональний допуск. Enrolled і Completed займають місця; Cancelled не займає. Один курс — один набір, завершення не відкриває нових місць. Після скасування можна зарахуватися повторно з новим reference. При зменшенні місткості нижче зайнятої кількості старі заявки зберігаються, нові не приймаються.

Після локальних перевірок один раз викликається EnrollmentPolicy.isAllowed(student, course). False — EnrollmentRejectedException; виняток залежності передається виклику. При локальній відмові policy не викликається. При відмовах запису немає.

Успіх створює Enrolled: total — ціна після персональної знижки, якщо вона є; prepaid=total; refund=0. couponValid — ознака вже підтвердженого промокоду, перевірка тексту кодів не входить до системи. Передоплата вважається внесеною, реальних платежів немає. Подальші зміни карток не змінюють збережені суми.

Дозволені переходи: Enrolled → Completed та Enrolled → Cancelled. Повторні переходи й скасування Completed заборонені. Завершення зберігає суми, скасування записує refund за збереженою prepaid. total і prepaid незмінні. Зовнішній дозвіл при переходах не викликається.

## Персональне правило — повернення зі збором

Від 10 днів до початку повертається вся передоплата. За 0–9 днів утримується 300 грн, але не більше передоплати. Повернення не може бути від’ємним.

Усі пороги включні. Гроші — цілі копійки. Відсоткова сума округлюється донизу один раз. Якщо правило не передбачає знижки — її немає; якщо не обмежує допуск — достатньо загальних перевірок; якщо не змінює повернення — повертається вся передоплата. Не згадані ознаки не впливають на результат.

Дні до початку — ціле число 0–36500; 0 означає день початку. Поточний час не використовується, скасування після початку не моделюється.

## API

`EnrollmentService(students, courses, enrollments, policy)`:

- `calculateTotal(price, regular, couponValid)` — сума навчання;
- `isEligible(age, entryScore, level, regular)` — персональний допуск; місця й зовнішній дозвіл перевіряє enroll;
- `calculateRefund(prepaid, daysBeforeStart)` — повернення, prepaid у межах 0–10 000 000 копійок;
- `enroll(new EnrollmentRequest(reference, studentId, courseId, couponValid))` — зарахування;
- `complete(id)` — завершення;
- `cancel(id, daysBeforeStart)` — скасування.

Моделі: Student(id, code, age, entryScore, regular), Course(id, code, price, capacity, level), Enrollment. Некоректні входи й відсутні записи — IllegalArgumentException; бізнес-відмови — EnrollmentRejectedException. Помилки БД обгортаються в DataAccessException.

StudentRepository та CourseRepository: create(model), findById(id), update(model), delete(id). EnrollmentRepository: create(model), findById(id), findEnrollments(courseId, status), countOccupied(courseId), hasEnrollment(studentId, courseId), updateStatus(id, next, refund), delete(id). Репозиторії можна підмінити Mockito або підкласами; EnrollmentPolicy — Stub/Mock або лямбдою.

create повертає модель з id БД, ігноруючи вхідний id. findById повертає null за відсутності запису. findEnrollments застосовує обидва фільтри одночасно та сортує за id. update відсутньої картки — IllegalArgumentException; updateStatus відсутньої або неактивної заявки — EnrollmentRejectedException. Видалення відсутнього запису нічого не змінює. Картки з будь-якими заявками видалити не можна; спочатку видаляються заявки.

У Spring репозиторії отримують JdbcTemplate з пулом з’єднань. Для попередніх тестів збережено конструктори з відкритим JDBC-з’єднанням з autoCommit=true; таке з’єднання закриває його власник через try-with-resources. Оформлення записує один рядок одним INSERT. Перевірка місткості й запис не захищені від конкурентного зарахування: використовуйте послідовні сценарії. Для зовнішнього дозволу доступна HTTP-реалізація HttpEnrollmentPolicy.

## Підготовка та запуск

У локальній PostgreSQL створіть користувача (один раз) та окрему БД:

```sql
CREATE ROLE courses_student LOGIN;
\password courses_student
CREATE DATABASE courses_variant18_test OWNER courses_student;
```

Із папки sut-b застосуйте міграцію один раз до порожньої БД:

```sh
psql -h localhost -U courses_student -d courses_variant18_test -v ON_ERROR_STOP=1 -f migrations/V1__create_tables.sql
```

Задайте змінні середовища за .env.example. Файл .env автоматично не читається; пароль — DB_PASSWORD.

```sh
mvn compile
mvn test
```

Є лише тест підключення. Дані для сценаріїв готуйте у власних тестах. Схеми та дані автоматично не створюються й не очищаються. Для незалежності тестів видаляйте власні заявки перед слухачами та курсами або використовуйте окрему тестову схему.

## HTTP-політика зарахування

`HttpEnrollmentPolicy` реалізує `EnrollmentPolicy` через HTTP. Адреса й таймаут задаються конструктором:

```java
var policy = new HttpEnrollmentPolicy(URI.create(wiremockUrl), Duration.ofSeconds(2));
var service = new EnrollmentService(students, courses, enrollments, policy);
```

Потрібні імпорти `java.net.URI` та `java.time.Duration`. `wiremockUrl` — базова HTTP(S)-адреса WireMock без шляху, з фактичним портом контейнера.

Після локальних перевірок сервіс надсилає один `POST /enrollment-policy` з `Content-Type: application/json`:

```json
{"studentId":1,"courseId":2}
```

HTTP 200 із `{"allowed":true}` дозволяє зберегти заявку зі статусом `Enrolled` і сумами за правилами варіанта. HTTP 200 із `{"allowed":false}` спричиняє `EnrollmentRejectedException` без нового запису. Інший статус, некоректний JSON, відсутнє або нелогічне поле `allowed`, мережева помилка чи таймаут спричиняють `IllegalStateException`; заявка також не створюється. Переривання потоку відновлює його ознаку переривання й спричиняє `IllegalStateException`. Перенаправлення вимкнені; адаптер не реалізує повторних спроб.

Локальна відмова до перевірки політики, зокрема відсутність місць чи наявне зарахування, не надсилає HTTP-запит. Завершення й скасування також не викликають HTTP-політику. Персональні правила залишаються чинними. У тестах перевіряйте фактичні записи PostgreSQL та кількість і тіло запитів у журналі WireMock.


## REST API

Запуск із папки sut-b: `mvn spring-boot:run` або клас `CoursesApplication` (Java 21).
Спочатку підготуйте PostgreSQL та застосуйте наявні міграції цього варіанта.
Параметри підключення: `DB_HOST`, `DB_PORT`, `DB_NAME` (типово `courses_variant18_test`), `DB_USER` (типово `courses_student`), `DB_PASSWORD`.
Адреса: `http://127.0.0.1:8080`. Порт можна змінити через `API_PORT`.
Файл `.env.example` є зразком; змінні середовища потрібно задати перед запуском.

| Метод | Шлях | Результат |
|---|---|---|
| POST | `/students` | Створений слухач, 201 та Location |
| GET | `/students/{id}` | Слухач із БД, 200 |
| POST | `/courses` | Створений курс, 201 та Location |
| GET | `/courses/{id}` | Курс із БД, 200 |
| POST | `/enrollments` | Зарахування через сервіс, 201 та Location |
| GET | `/enrollments/{id}` | Збережена заявка, 200 |
| POST | `/enrollments/{id}/complete` | Завершення курсу, 200 |
| POST | `/enrollments/{id}/cancel` | Скасування з розрахунком повернення, 200 |

Приклади тіл POST із `Content-Type: application/json`:

- Слухач: `{"code": "ST-001", "age": 20, "entryScore": 90, "regular": false}`.
- Курс: `{"code": "CR-001", "price": 100000, "capacity": 10, "level": 1}`.
- Зарахування: `{"reference": "EN-001", "studentId": 1, "courseId": 1, "couponValid": false}`.
- Скасування: `{"daysBeforeStart":7}`.

Підставляйте ідентифікатори з попередніх відповідей. Завершення виконується без тіла запиту.
Гроші передаються цілими копійками. Персональні правила з опису вище залишаються чинними.
Повторним GET перевіряйте `total`, `prepaid`, `refund`, `status`. Статуси: `Enrolled`, `Completed`, `Cancelled`.
Відсутній ресурс повертає 404, бізнес-відмова або недозволений перехід — 409, некоректний запит — 400.
Помилки мають JSON `{"code":"...","message":"..."}`.

`ENROLLMENT_POLICY_URL` вмикає наявну зовнішню HTTP-перевірку. Без цієї змінної локальна політика дозволяє операцію; перевірки сервісу залишаються чинними.
Недоступність зовнішньої перевірки повертає 502. API не застосовує міграції й не очищає БД автоматично. Сценарії виконуйте послідовно: перевірка місткості та зарахування не захищені від конкурентного виконання.
