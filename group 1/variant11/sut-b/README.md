# SUT B — Реєстрація на навчальні курси, варіант 11

JavaScript, Node.js 22+, PostgreSQL 17, pg та Jest.

## Спільні правила

Слухач має унікальний непорожній code до 40 символів, вік 14–100 років, вступний бал 0–100 та ознаку regular. Курс має окремий унікальний непорожній code до 40 символів, ціну 100–10 000 000 копійок, місткість 1–100 місць і рівень 1–3. Вік, бал і рівень зберігаються, але в цьому варіанті не впливають на допуск. Знижок немає.

Заявка містить одного слухача й один курс. reference — унікальний непорожній рядок до 40 символів, ідентифікатори — додатні безпечні цілі JavaScript. Числові поля мають бути цілими, regular — boolean. Обмеження карток перевіряє БД.

Зарахування перевіряє заявку, існування слухача й курсу, відсутність попередньої нескасованої заявки та наявність місця. Enrolled і Completed займають місця; Cancelled не займає. Один курс означає один набір слухачів, завершення навчання не відкриває новий набір. Повторне зарахування після скасування можливе з новим reference. Якщо місткість зменшено нижче кількості зайнятих місць, наявні заявки зберігаються, нові не приймаються.

Після локальних перевірок один раз викликається policy.isAllowed(student, course). False — EnrollmentRejectedError, відповідь іншого типу — TypeError. Виняток залежності передається виклику. При відмовах запису немає.

Успіх створює Enrolled із total, рівним поточній ціні курсу, prepaid=total та refund=0. У навчальній моделі повна передоплата вважається внесеною; реальних платежів немає. Подальша зміна ціни курсу не змінює суми заявки.

Дозволені переходи: Enrolled → Completed та Enrolled → Cancelled. Повторні переходи й скасування Completed заборонені. Завершення зберігає суми, скасування записує refund. Зовнішній дозвіл при цих операціях не викликається.

## Персональне правило — повернення при скасуванні

Від 7 днів до початку включно повертається 100% prepaid; за 2–6 днів — 50%; за 0–1 день — 0%. Дні — ціле число 0–36500; 0 означає день початку. Дні передаються аргументом, поточний час не використовується. Скасування після початку не моделюється.

Повернення розраховується за збереженою передоплатою й округлюється донизу до цілої копійки. total і prepaid залишаються незмінними.

## API

`EnrollmentService(students, courses, enrollments, policy)`:

- `calculateRefund(prepaid, daysBeforeStart)` — повернення; prepaid у межах 0–10 000 000 копійок;
- `enroll(new EnrollmentRequest(reference, studentId, courseId))` — зарахування;
- `complete(id)` — завершення навчання;
- `cancel(id, daysBeforeStart)` — скасування.

Моделі: Student(id, code, age, entryScore, regular=false), Course(id, code, price, capacity, level=1), Enrollment. Всі експортуються з src/models.js.

Некоректні входи — TypeError/RangeError; відсутні записи — RangeError; бізнес-відмови — EnrollmentRejectedError. Помилки БД передаються виклику.

StudentRepository та CourseRepository: create(model), findById(id), update(model), delete(id). EnrollmentRepository: create(model), findById(id), findEnrollments(courseId, status), countOccupied(courseId), hasEnrollment(studentId, courseId), updateStatus(id, next, refund), delete(id).

create ігнорує вхідний id та повертає збережену модель з id БД. findById повертає null за відсутності запису. findEnrollments застосовує обидва фільтри одночасно й сортує за id. update відсутньої картки — RangeError; updateStatus відсутньої або неактивної заявки — EnrollmentRejectedError. Видалення відсутнього запису нічого не змінює. Картку з будь-якими заявками видалити не можна: спочатку видаляються заявки.

Усі методи з БД асинхронні, використовуйте await. Репозиторії отримують відкритий pg.Client; власник закриває його через await client.end(). Залежності можна замінити об’єктами з відповідними методами; policy — об’єкт із isAllowed. Для перевірки через HTTP доступна реалізація HttpEnrollmentPolicy.

Оформлення записує один рядок одним INSERT. Перевірка місткості й запис не захищені від конкурентних зарахувань: цей SUT призначений для послідовних сценаріїв, не запускайте операції паралельно.

## Підготовка та запуск

Із папки sut-b:

```sh
npm install
```

У локальній PostgreSQL створіть користувача (один раз) і окрему БД:

```sql
CREATE ROLE courses_student LOGIN;
\password courses_student
CREATE DATABASE courses_variant11_test OWNER courses_student;
```

Застосуйте міграцію один раз до порожньої БД:

```sh
psql -h localhost -U courses_student -d courses_variant11_test -v ON_ERROR_STOP=1 -f migrations/V1__create_tables.sql
```

Задайте змінні середовища за .env.example. Файл .env автоматично не читається; пароль — DB_PASSWORD.

```sh
npm test
```

Є лише тест підключення. Дані для сценаріїв готуйте у власних тестах. Схеми та дані автоматично не створюються й не очищаються. Для незалежності тестів видаляйте власні заявки перед слухачами та курсами або використовуйте окрему тестову схему.

## HTTP-політика зарахування

`HttpEnrollmentPolicy` реалізує `EnrollmentPolicy` через вбудований `fetch` Node.js 22. Додаткові пакети не потрібні.

```js
const { HttpEnrollmentPolicy } = require("./src/http-enrollment-policy");
const policy = new HttpEnrollmentPolicy(wiremockUrl, 2000);
const service = new EnrollmentService(students, courses, enrollments, policy);
```

`wiremockUrl` — базова HTTP(S)-адреса WireMock без шляху, з фактичним портом контейнера. Другий аргумент — таймаут у мілісекундах, ціле число від 1 до 2147483647; охоплює очікування відповіді та читання її тіла.

Після локальних перевірок сервіс надсилає один `POST /enrollment-policy` з `Content-Type: application/json`:

```json
{"studentId":1,"courseId":2}
```

HTTP 200 із `{"allowed":true}` дозволяє зберегти заявку зі статусом `Enrolled`, total і prepaid, рівними ціні курсу, та refund=0. HTTP 200 із `{"allowed":false}` спричиняє `EnrollmentRejectedError` без нового запису. Інший статус, некоректний JSON, відсутнє або нелогічне поле `allowed`, мережева помилка чи таймаут спричиняють `EnrollmentPolicyError`; заявка також не створюється. Перенаправлення вимкнені; адаптер не реалізує повторних спроб.

Локальна відмова, зокрема відсутність місць чи наявне зарахування, не надсилає HTTP-запит. Завершення й скасування також не викликають HTTP-політику. Персональне правило повернення коштів залишається чинним. У тестах перевіряйте фактичні записи PostgreSQL та кількість і тіло запитів у журналі WireMock.
