# SUT B — Реєстрація на навчальні курси, варіант 08

Python 3.12+, PostgreSQL 17, psycopg та pytest.

## Спільні правила

Слухач має унікальний непорожній code до 40 символів, вік 14–100, вступний бал 0–100 та ознаку regular. Курс має окремий унікальний непорожній code до 40 символів, ціну 100–10 000 000 копійок, місткість 1–100 і рівень 1–3. Заявка містить одного слухача й один курс. reference унікальний, непорожній, до 40 символів. Ідентифікатори заявок — додатні цілі до 2⁶³−1; bool не допускається замість чисел. Логічні ознаки — лише bool.

Зарахування перевіряє заявку, картки, відсутність нескасованої заявки, вільне місце та персональний допуск. Enrolled і Completed займають місця; Cancelled не займає. Один курс — один набір: завершення не відкриває нових місць. Після скасування можна зарахуватися повторно з новим reference. Якщо місткість зменшено нижче зайнятої кількості, старі заявки зберігаються, нові не приймаються.

Потім один раз викликається policy.is_allowed(student, course). False — EnrollmentRejectedError, відповідь іншого типу — ValueError; виняток залежності передається виклику. При локальній відмові policy не викликається. При відмовах запису немає.

Успіх створює Enrolled. total — ціна після персональної знижки, якщо вона є; prepaid=total; refund=0. Передоплата вважається внесеною, реальних платежів немає. Подальші зміни карток не перераховують збережені суми. coupon_valid означає вже підтверджений промокод; перевірка текстових кодів не входить до системи.

Дозволені переходи: Enrolled → Completed та Enrolled → Cancelled. Повторні переходи й скасування Completed заборонені. Завершення зберігає суми. Скасування записує refund за збереженою prepaid; total і prepaid незмінні. Зовнішній дозвіл при переходах не викликається.

## Персональне правило — комбінована знижка

5% постійному слухачу та 10% від ціни 2000 грн. Відсотки додаються: 0%, 5%, 10% або 15%.

Усі пороги включні. Гроші — цілі копійки. Загальна відсоткова сума округлюється донизу один раз; потім застосовуються мінімум або максимум. Якщо правило не передбачає знижки — її немає; якщо не обмежує допуск — достатньо загальних перевірок; якщо не змінює повернення — повертається вся передоплата. Не згадані в правилі ознаки на результат не впливають.

Дні до початку — ціле число 0–36500, передане аргументом; 0 — день початку. Поточний час не використовується, скасування після початку не моделюється.

## API

`EnrollmentService(students, courses, enrollments, policy)`:

- `calculate_total(price, regular=False, coupon_valid=False)` — сума навчання;
- `is_eligible(age, entry_score, level)` — персональний допуск; місця та зовнішній дозвіл перевіряє enroll;
- `calculate_refund(prepaid, days_before_start)` — повернення, prepaid у межах 0–10 000 000 копійок;
- `enroll(EnrollmentRequest(reference, student_id, course_id, coupon_valid=False))` — зарахування;
- `complete(enrollment_id)` — завершення;
- `cancel(enrollment_id, days_before_start=0)` — скасування.

Student(id, code, age, entry_score, regular=False), Course(id, code, price, capacity, level=1) та Enrollment — незмінні моделі. Некоректні входи й відсутні записи — ValueError; бізнес-відмови — EnrollmentRejectedError. Помилки БД передаються виклику.

PostgresStudentRepository та PostgresCourseRepository: create(model), find_by_id, update(model), delete. PostgresEnrollmentRepository: create(model), find_by_id, find_enrollments(course_id, status), count_occupied(course_id), has_enrollment(student_id, course_id), update_status(enrollment_id, next_status, refund), delete. Контракти залежностей для тестових двійників — у courses/ports.py.

create повертає модель з id БД, ігноруючи вхідний id. find_by_id повертає None за відсутності запису. find_enrollments застосовує обидва фільтри одночасно та сортує за id. update відсутньої картки — ValueError; update_status відсутньої або неактивної заявки — EnrollmentRejectedError. Видалення відсутнього запису нічого не змінює. Картки з будь-якими заявками видалити не можна: спочатку видаляються заявки.

Репозиторії отримують відкрите з’єднання від courses.database.connect з autocommit=True; власник закриває його через with. Оформлення записує один рядок одним INSERT. Перевірка місткості й запис не захищені від конкурентного зарахування: використовуйте послідовні сценарії. Передайте policy як тестовий двійник; реальних зовнішніх інтеграцій немає.

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
CREATE ROLE courses_student LOGIN;
\password courses_student
CREATE DATABASE courses_variant08_test OWNER courses_student;
```

Застосуйте міграцію один раз до порожньої БД:

```sh
psql -h localhost -U courses_student -d courses_variant08_test -v ON_ERROR_STOP=1 -f migrations/V1__create_tables.sql
```

Задайте змінні середовища за .env.example. Файл .env автоматично не читається; пароль — DB_PASSWORD.

```sh
python -m pytest
```

Є лише тест підключення. Дані для сценаріїв готуйте у власних тестах. Схеми та дані автоматично не створюються й не очищаються. Для незалежності тестів видаляйте власні заявки перед слухачами та курсами або використовуйте окрему тестову схему.
