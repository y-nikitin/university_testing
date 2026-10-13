# SUT B — Реєстрація на навчальні курси, варіант 02

C#, .NET 8 SDK, PostgreSQL 17, Npgsql, xUnit та Moq.

## Спільні правила

Слухач має унікальний непорожній Code до 40 символів, вік 14–100, вступний бал 0–100 та ознаку Regular. Курс має окремий унікальний непорожній Code до 40 символів, ціну 100–10 000 000 копійок, місткість 1–100 місць і рівень 1–3. Вік, бал і рівень у цьому варіанті не впливають на допуск. Обмеження карток перевіряє БД.

Заявка містить одного слухача та один курс. Reference унікальний, непорожній, до 40 символів; ідентифікатори додатні. Зарахування перевіряє заявку, існування карток, відсутність нескасованої заявки та наявність місця. Enrolled і Completed займають місця; Cancelled не займає. Один курс — один набір, завершення навчання не відкриває нових місць. Після скасування можна зарахуватися повторно з новим Reference. Зменшення місткості нижче зайнятої кількості зберігає старі заявки, але блокує нові.

Після локальних перевірок один раз викликається IEnrollmentPolicy.IsAllowed(student, course). False — EnrollmentRejectedException; виняток залежності передається виклику. При відмовах запису немає.

Успіх створює Enrolled із Total, рівним ціні курсу, Prepaid за персональним правилом та Refund=0. Знижок немає. Суми фіксуються при оформленні: подальші зміни ціни чи Regular їх не змінюють. Prepaid вважається внесеною; реальних платежів немає.

Дозволені переходи: Enrolled → Completed та Enrolled → Cancelled. Повторні переходи й скасування Completed заборонені. Завершення зберігає суми. При скасуванні Refund дорівнює збереженій Prepaid, Total і Prepaid незмінні. Зовнішній дозвіл при переходах не викликається. Календар і доплата залишку не моделюються: завершення не вимагає додаткової оплати.

## Персональне правило — передоплата

Передоплата — 30% ціни курсу, для постійного слухача — 10%.

Гроші — цілі копійки. Відсоткова сума округлюється донизу один раз, потім застосовуються мінімум та максимум. Ознаки, не згадані в правилі, не впливають на суму.

## API

`EnrollmentService(students, courses, enrollments, policy)`:

- `CalculatePrepayment(total, regular=false)` — передоплата; total від 100 до 10 000 000 копійок;
- `Enroll(new EnrollmentRequest(reference, studentId, courseId))` — зарахування;
- `Complete(id)` — завершення;
- `Cancel(id)` — скасування з повним поверненням передоплати.

Моделі: Student(id, code, age, entryScore, regular=false), Course(id, code, price, capacity, level=1), Enrollment. Некоректні входи й відсутні записи — ArgumentException або підтипи; бізнес-відмови — EnrollmentRejectedException. Помилки БД передаються виклику.

StudentRepository та CourseRepository: Create(model), FindById(id), Update(model), Delete(id). EnrollmentRepository: Create(model), FindById(id), FindEnrollments(courseId, status), CountOccupied(courseId), HasEnrollment(studentId, courseId), UpdateStatus(id, next, refund), Delete(id). Залежності мають інтерфейси для Stub, Mock, Spy та Fake.

Create повертає модель з id БД, ігноруючи вхідний id. FindById повертає null за відсутності запису. FindEnrollments застосовує обидва фільтри одночасно та сортує за id. Update відсутньої картки — ArgumentException; UpdateStatus відсутньої чи неактивної заявки — EnrollmentRejectedException. Delete відсутнього запису нічого не змінює. Картку з будь-якими заявками видалити не можна; спочатку видаляються заявки.

Репозиторії отримують відкрите ADO.NET-з’єднання, яке власник закриває через using. Оформлення записує один рядок одним INSERT. Перевірка місткості й запис не захищені від конкурентного зарахування: використовуйте послідовні сценарії. Передавайте IEnrollmentPolicy як тестовий двійник або HttpEnrollmentPolicy для перевірки через HTTP.

## Підготовка та запуск

У локальній PostgreSQL створіть користувача (один раз) та окрему БД:

```sql
CREATE ROLE courses_student LOGIN;
\password courses_student
CREATE DATABASE courses_variant02_test OWNER courses_student;
```

Із папки sut-b застосуйте міграцію один раз до порожньої БД:

```sh
psql -h localhost -U courses_student -d courses_variant02_test -v ON_ERROR_STOP=1 -f migrations/V1__create_tables.sql
```

Задайте змінні середовища за .env.example. Файл .env автоматично не читається; пароль — DB_PASSWORD.

```sh
dotnet build Courses.csproj
dotnet test tests/Courses.Tests.csproj
```

Є лише тест підключення. Дані для сценаріїв готуйте у власних тестах. Схеми та дані автоматично не створюються й не очищаються. Для незалежності тестів видаляйте власні заявки перед слухачами й курсами або використовуйте окрему тестову схему.

## HTTP-політика зарахування

`HttpEnrollmentPolicy` реалізує `IEnrollmentPolicy` через HTTP. Додаткові пакети не потрібні.

```csharp
using var policy = new HttpEnrollmentPolicy(new Uri(wiremockUrl), TimeSpan.FromSeconds(2));
var service = new EnrollmentService(students, courses, enrollments, policy);
```

`wiremockUrl` — базова HTTP(S)-адреса WireMock без шляху, з фактичним портом контейнера. Таймаут має бути додатним і скінченним. Власник звільняє політику через `using` після завершення викликів сервісу.

Після локальних перевірок сервіс надсилає один `POST /enrollment-policy` з `Content-Type: application/json`:

```json
{"studentId":1,"courseId":2}
```

HTTP 200 із `{"allowed":true}` дозволяє зберегти заявку зі статусом `Enrolled` і сумами за правилами варіанта. HTTP 200 із `{"allowed":false}` спричиняє `EnrollmentRejectedException` без нового запису. Інший статус, некоректний JSON, відсутнє або нелогічне поле `allowed`, мережева помилка чи таймаут спричиняють `EnrollmentPolicyException`; заявка також не створюється. Перенаправлення вимкнені; адаптер не реалізує повторних спроб.

Локальна відмова до перевірки політики, зокрема відсутність місць чи наявне зарахування, не надсилає HTTP-запит. Завершення й скасування також не викликають HTTP-політику. Персональні правила залишаються чинними. У тестах перевіряйте фактичні записи PostgreSQL та кількість і тіло запитів у журналі WireMock.


## REST API

Запуск із папки sut-b: `dotnet run --project Courses.csproj` (.NET SDK 8).
Спочатку підготуйте PostgreSQL та застосуйте наявні міграції цього варіанта.
Параметри підключення: `DB_HOST`, `DB_PORT`, `DB_NAME` (типово `courses_variant02_test`), `DB_USER` (типово `courses_student`), `DB_PASSWORD`.
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
- Зарахування: `{"reference": "EN-001", "studentId": 1, "courseId": 1}`.
- Скасування: без тіла запиту.

Підставляйте ідентифікатори з попередніх відповідей. Завершення виконується без тіла запиту.
Гроші передаються цілими копійками. Персональні правила з опису вище залишаються чинними.
Повторним GET перевіряйте `total`, `prepaid`, `refund`, `status`. Статуси: `Enrolled`, `Completed`, `Cancelled`.
Відсутній ресурс повертає 404, бізнес-відмова або недозволений перехід — 409, некоректний запит — 400.
Помилки мають JSON `{"code":"...","message":"..."}`.

`ENROLLMENT_POLICY_URL` вмикає наявну зовнішню HTTP-перевірку. Без цієї змінної локальна політика дозволяє операцію; перевірки сервісу залишаються чинними.
Недоступність зовнішньої перевірки повертає 502. API не застосовує міграції й не очищає БД автоматично. Сценарії виконуйте послідовно: перевірка місткості та зарахування не захищені від конкурентного виконання.
