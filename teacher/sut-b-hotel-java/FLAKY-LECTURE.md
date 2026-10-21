# Практичний блок лекції «Регресійні та flaky-тести» (п. 7)

Усі приклади лежать у `src/test/java/hotel/flaky/`. Код викладача не змінено; єдина зміна в `pom.xml` — залежність `awaitility` (test).

| Кейс | Поламаний | Виправлений | Потрібно |
|---|---|---|---|
| 1. Порядок і брудні дані | `case1/BadBookingRepositoryTest` | `case1/FixedBookingRepositoryTest` | локальна PostgreSQL |
| 2. Thread.sleep | `case2/AsyncStatusBadTest` | `case2/AsyncStatusFixedTest` (+ `AsyncBookingProcessor`) | нічого |
| 3. Час і часовий пояс | `case3/DatePolicyBadTest` (+ `BadDatePolicy`) | `case3/DatePolicyFixedTest` (+ `DatePolicy`) | нічого |
| 4. Гонка за даними | `case4/RoomRaceBadIT` | `case4/RoomRaceFixedIT` | локальна PostgreSQL, міграції в `public` |

Поламані класи за замовчуванням пропускаються (щоб `mvn test` лишався зеленим). Вмикаються VM-опцією `-Ddemo.broken=true`.
Класи `*IT` не входять у `mvn test`: запускайте з IDE або `mvn test -Dtest=RoomRace*`.

Підготовка БД для кейсів 1 і 4 — як у `README.md` (змінні `DB_URL`, `DB_USER`, `DB_PASSWORD`, міграції V1 і V2).
Робоча директорія тестів має бути коренем проєкту (`migrations/` читається за відносним шляхом).

## Як повторювати в IntelliJ IDEA

Run Configuration (JUnit) -> **Modify options** -> **Add VM options**; у цій же конфігурації є поле **Repeat** (N Times / Until Failure).

| Що досліджуємо | Клас | VM options | Repeat |
|---|---|---|---|
| Кейс 1, bad (падає приблизно в половині запусків) | `BadBookingRepositoryTest` | `-Ddemo.broken=true` | N Times = 50 |
| Кейс 1, fix | `FixedBookingRepositoryTest` | — | N Times = 50 |
| Кейс 2, bad «на моїй машині» | `AsyncStatusBadTest` | `-Ddemo.broken=true -Ddemo.delayMs=300` | Once |
| Кейс 2, bad «на CI» | `AsyncStatusBadTest` | `-Ddemo.broken=true -Ddemo.delayMs=1200` | Once |
| Кейс 2, fix (затримки 0 / 300 / 1200 / 3000 мс) | `AsyncStatusFixedTest` | — | Once |
| Кейс 3, bad | `DatePolicyBadTest` | `-Ddemo.broken=true -Duser.timezone=Pacific/Kiritimati` (червоний ~з 10:00 до 24:00 UTC) або `Pacific/Pago_Pago` (зелений) | Once |
| Кейс 3, fix | `DatePolicyFixedTest` | `-Duser.timezone=Pacific/Kiritimati` (потім `Pacific/Pago_Pago`) — завжди зелений | Once |
| Кейс 4, bad | `RoomRaceBadIT` | `-Ddemo.broken=true -Djunit.jupiter.execution.parallel.enabled=true -Djunit.jupiter.execution.parallel.config.strategy=fixed -Djunit.jupiter.execution.parallel.config.fixed.parallelism=8` | N Times = 20 |
| Кейс 4, fix | `RoomRaceFixedIT` | ті самі прапорці паралельності, але без `demo.broken` | N Times = 20 |

Без `parallel.enabled=true` кейс 4 виконується послідовно й «чудово проходить».

## Maven (за потреби)

```sh
mvn test -Dtest=FixedBookingRepositoryTest,AsyncStatusFixedTest,DatePolicyFixedTest
mvn test -Dtest=RoomRaceFixedIT -Djunit.jupiter.execution.parallel.enabled=true -Djunit.jupiter.execution.parallel.config.strategy=fixed -Djunit.jupiter.execution.parallel.config.fixed.parallelism=8
mvn test -Dsurefire.runOrder=random      # випадковий порядок КЛАСІВ (методів не чіпає)
```
