# SUT B — Реєстрація на навчальні курси, варіант 04

Java 21, Maven, PostgreSQL 17, JDBC, JUnit 5 та Mockito.

## Спільні правила

Слухач має унікальний непорожній code до 40 символів, вік 14–100, вступний бал 0–100 та ознаку regular. Курс має окремий унікальний непорожній code до 40 символів, ціну 100–10 000 000 копійок, місткість 1–100 і рівень 1–3. Заявка містить одного слухача й один курс. reference унікальний, непорожній, до 40 символів; ідентифікатори додатні.

Зарахування перевіряє заявку, існування карток, відсутність нескасованої заявки, вільне місце та персональний допуск. Enrolled і Completed займають місця; Cancelled не займає. Один курс — один набір, завершення не відкриває нових місць. Після скасування можна зарахуватися повторно з новим reference. При зменшенні місткості нижче зайнятої кількості старі заявки зберігаються, нові не приймаються.

Після локальних перевірок один раз викликається EnrollmentPolicy.isAllowed(student, course). False — EnrollmentRejectedException; виняток залежності передається виклику. При локальній відмові policy не викликається. При відмовах запису немає.

Успіх створює Enrolled: total — ціна після персональної знижки, якщо вона є; prepaid=total; refund=0. couponValid — ознака вже підтвердженого промокоду, перевірка тексту кодів не входить до системи. Передоплата вважається внесеною, реальних платежів немає. Подальші зміни карток не змінюють збережені суми.

Дозволені переходи: Enrolled → Completed та Enrolled → Cancelled. Повторні переходи й скасування Completed заборонені. Завершення зберігає суми, скасування записує refund за збереженою prepaid. total і prepaid незмінні. Зовнішній дозвіл при переходах не викликається.

## Персональне правило — знижка за ціною

Від 1000 грн — 5%, від 3000 грн — 12%. Застосовується лише більша знижка.

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

Моделі: Student(id, code, age, entryScore, regular), Course(id, code, price, capacity, level), Enrollment. Некоректні входи й відсутні записи — IllegalArgumentException; бізнес-відмови — EnrollmentRejectedException. Помилки БД передаються як SQLException.

StudentRepository та CourseRepository: create(model), findById(id), update(model), delete(id). EnrollmentRepository: create(model), findById(id), findEnrollments(courseId, status), countOccupied(courseId), hasEnrollment(studentId, courseId), updateStatus(id, next, refund), delete(id). Репозиторії можна підмінити Mockito або підкласами; EnrollmentPolicy — Stub/Mock або лямбдою.

create повертає модель з id БД, ігноруючи вхідний id. findById повертає null за відсутності запису. findEnrollments застосовує обидва фільтри одночасно та сортує за id. update відсутньої картки — IllegalArgumentException; updateStatus відсутньої або неактивної заявки — EnrollmentRejectedException. Видалення відсутнього запису нічого не змінює. Картки з будь-якими заявками видалити не можна; спочатку видаляються заявки.

Репозиторії отримують відкрите JDBC-з’єднання з autoCommit=true, власник закриває його через try-with-resources. Оформлення записує один рядок одним INSERT. Перевірка місткості й запис не захищені від конкурентного зарахування: використовуйте послідовні сценарії. Для зовнішнього дозволу доступна HTTP-реалізація HttpEnrollmentPolicy.

## Підготовка та запуск

У локальній PostgreSQL створіть користувача (один раз) та окрему БД:

```sql
CREATE ROLE courses_student LOGIN;
\password courses_student
CREATE DATABASE courses_variant04_test OWNER courses_student;
```

Із папки sut-b застосуйте міграцію один раз до порожньої БД:

```sh
psql -h localhost -U courses_student -d courses_variant04_test -v ON_ERROR_STOP=1 -f migrations/V1__create_tables.sql
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
