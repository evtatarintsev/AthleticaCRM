## Context

- `memberships` (0059): `start_date`/`end_date NOT NULL`, `sessions_total`/`sessions_remaining`, без состояния, групп и режимов. `Membership` — интерфейс с полями и `save()` (только INSERT), `DbMemberships.new` вычисляет `endDate` от `startDate` и срока.
- Статус считается в `MembershipsRoutes.toSchema()` по `TimeZone.currentSystemDefault()`; `membershipEndingSoon` — в `web/src/clients/card/clientCardAlerts.ts`.
- Журнал посещаемости публикует `SessionCompleted` (итоговый состав) и `AttendanceChanged` (в том числе после проведения) через outbox: `DomainEventWorker` вызывает `DomainEventHandler.handle(orgId, event)` асинхронно, в своей транзакции, at-least-once.
- Есть `SystemRequestContext` для действий без сотрудника. Часовой пояс организации — `organizations.timezone`, в SQL уже используется `AT TIME ZONE o.timezone`.
- `tasks.created_by NOT NULL`; видимость без `CAN_VIEW_ALL_TASKS` — `(assignee_id = :me OR created_by = :me)` в `DbTaskListView`; `DbTaskDetailView` читает `created_by` как не-null, `TaskDetailResponse.createdBy: EmployeeId`. У клиента нет ответственного сотрудника. Уведомления сотрудникам — `domain/notifications`.
- `DomainEventWorker` повторяет упавшее событие на каждом опросе (раз в секунду) и после 5 попыток помечает его `processed_at` — событие теряется.
- `DbMemberships.endDate` считает `start + duration`, а статус считает дату окончания включительной — у дневного срока лишний день.
- Контракты для веба генерируются из `shared`, sealed-схемы поддерживаются (`NotificationsSchema`, `MessagingSchemas`).

## Goals / Non-Goals

**Goals:**
- Недопустимое действие непредставимо: метода нет у типа состояния.
- Выводимые свойства — чистые функции от абонемента и даты, тестируются без БД.
- Списания идемпотентны.
- Одно правило подбора абонемента для списания и для превью.
- Итог списаний по одному участию не зависит от повторной доставки и порядка его событий (кроме даты начала при первом посещении, п. 6).

**Non-Goals:**
- Событийные (материализованные) проекции абонементов.
- Замена `sessions_remaining` вычислением по списаниям.
- Точное время фоновой обработки (ровно в полночь организации).

## Decisions

### 1. Состояние — sealed-иерархия, переход возвращает новый тип

```
sealed interface Membership            id, clientId, tariffPlanId, name, sessionsTotal,
 |                                     duration, price, groups, closeMode, issuedOn, issuedBy
 +- InactiveMembership                 sealed: OnDate(activateOn) | OnFirstVisit(deadline?)
 |     activate(startDate) -> ActiveMembership
 |     revoke()            -> RevokedMembership
 +- ActiveMembership                   startDate, endDate, sessionsRemaining
 |     statusAt(date): ActiveStatus    чистая функция
 |     debit(session, date)            -> ActiveMembership | ClosedMembership
 |     repayDebt(sessions)             -> ActiveMembership | ClosedMembership
 |     close(reason)                   -> ClosedMembership
 |     revoke()                        -> RevokedMembership   (нет действующих списаний)
 |     reverse(debit)                  -> ActiveMembership
 +- ClosedMembership                   closedOn, reason
 |     reverse(debit)                  -> ClosedMembership
 +- RevokedMembership
```

`ActiveStatus` — значение: `termPosition` (`NotStarted | InTerm | Expired`), `remaining` (`Left(n) | Exhausted | Unlimited`), `endingSoon: Set<EndingSoonReason>`, `closePending: Boolean`. Пороги (7 дней, 1 занятие) — константы в домене.

Методы переходов — команды: пишут строку `memberships`, событие истории и возвращают новый тип. Usecase получает абонемент из `Memberships.byId` и делает `when` только там, где нужен конкретный тип (`as? ActiveMembership ?: raise(...)`), — это одна точка, а не проверка статуса в каждом usecase.

Альтернатива — один класс с полем `state` и проверками в методах. Отвергнута: issue прямо требует непредставимости.

### 2. Хранение

Новые колонки `memberships`: `state` (`INACTIVE | ACTIVE | CLOSED | REVOKED`), `activation_mode` (`IMMEDIATE | ON_DATE | ON_FIRST_VISIT`), `activate_on`, `activation_deadline`, `duration_value`, `duration_unit`, `close_mode` (`MANUAL | ON_LAST_SESSION | ON_EXPIRY`), `issued_on DATE`, `close_notified_at`, `closed_on`, `close_reason`. `start_date`/`end_date` становятся nullable. CHECK-ограничения: у `ACTIVE`/`CLOSED` даты заданы; у `ON_FIRST_VISIT`, `INACTIVE` дат нет; `ON_LAST_SESSION` требует `sessions_total`.

`DbMemberships` маппит строку в нужный подтип. Таблицы:
- `membership_groups (membership_id, group_id)`;
- `membership_events (id, org_id, membership_id, occurred_at, actor_id NULL, kind, payload JSONB)` — история; `actor_id NULL` = система;
- `membership_debits (id, org_id, membership_id, client_id, session_id, kind REGULAR|DEBT, created_at, reversed_at NULL)` с уникальным частичным индексом `(session_id, client_id) WHERE reversed_at IS NULL` — не больше одного действующего списания за участие.

`close_notified_at` — не состояние, а отметка об отправленном уведомлении «к закрытию» для текущего эпизода. Источник правды для «к закрытию» — `statusAt(today)`. `reverse`, после которого абонемент перестал быть «к закрытию», в той же транзакции сбрасывает `close_notified_at` в `NULL`, открывая следующий эпизод.

`sessions_remaining` остаётся колонкой и меняется в той же транзакции, что и списание. Конкурентность — `SELECT ... FOR UPDATE` абонемента в `byId` для команд.

### 3. История — своя таблица, не `AuditLog`

`AuditLog` best-effort и не транзакционен (CLAUDE.md, «Known Constraints» п. 5), а история видна пользователю и должна совпадать с состоянием. Поэтому событие истории пишет сам метод перехода в той же транзакции. Аудит (`AuditMemberships`) остаётся как есть для операций сотрудников.

### 4. «Сегодня» — по часовому поясу организации; срок включительный

Дата окончания = `startDate.minus(1 day).plus(duration)`; `kotlinx.datetime` при сложении месяцев обрезает число до последнего дня месяца, это и даёт сценарии спеки (31.01 + 1 месяц → 28/29.02). Альтернатива `start + duration − 1 день` отвергнута: для 31.01 даёт 27.02, теряя дни. Функция переезжает из `DbMemberships` в домен и покрывается юнит-тестами сценариев «Срок действия включает…».


`OrgCalendar` с методом `today()`: часовой пояс из `organizations`, время из `kotlin.time.Clock` (подменяется в тестах). Домен получает дату параметром (`statusAt(date)`, `activate(startDate)`), а не читает часы сам. Маршрут списка абонементов берёт `today()` у `OrgCalendar`, фоновая обработка — из SQL `(now() AT TIME ZONE o.timezone)::date`.

### 5. Контекст: `RequestContext`, автор — из контекста

Методы абонемента принимают `context(ctx: RequestContext, ...)`. Автор события истории: `EmployeeRequestContext.employee.id`, для `SystemRequestContext` — `null`. Обработчики событий и фоновая обработка работают в `SystemRequestContext(orgId)`.

### 6. Списания — сверка по текущему состоянию, а не дельта по событию

Обработчики `SessionCompleted` и `AttendanceChanged` для каждой пары (занятие, клиент) выполняют одну операцию: «должно быть списание» = занятие проведено и отметка сейчас «пришёл». Состояние читается из БД, а не из события:
- должно быть, нет действующего → подобрать абонемент и списать;
- не должно быть, есть действующее → вернуть (`reverse`);
- иначе ничего.

Так повторная доставка и `AttendanceChanged` по открытому журналу безопасны, а итог списаний и возвратов по одному участию не зависит от порядка его событий. Исключение — активация первым посещением: дата начала — дата занятия, чьё проведение обработано первым; более раннее занятие, обработанное позже, становится долгом. Это следствие правила «дата активации не переписывается» и зафиксировано сценарием спеки; сортировка обработки занятий клиента по дате отвергнута — она требует буферизации событий и всё равно не помогает при правке отметки спустя дни. «Нет подходящего абонемента» — нормальный исход (`Unit`), а не исключение: исключение означает инфраструктурный сбой и уходит в повтор (п. 11).

Первое посещение — одна транзакция обработчика, порядок фиксирован: `activate(sessionDate)` → `debit(session)` → `UnpaidSessionsView` по клиенту и группам абонемента → задача, если долгов > 0. Проверка долгов идёт после записи списания в той же транзакции, поэтому активировавшее занятие уже не неоплаченное и в счёт не попадает. Ошибка на любом шаге откатывает все четыре. Синхронное списание в транзакции проведения отвергнуто: журнал стал бы зависеть от абонементов, а ошибка подбора ломала бы проведение.

### 7. Правило подбора — один SQL в домене и его копия в превью

`Memberships.debitCandidate(clientId, groupId, sessionDate)` — один SQL с порядком из спеки, `ORDER BY ... LIMIT 1 FOR UPDATE`: найденный абонемент сразу блокируется в транзакции обработчика. После блокировки домен заново проверяет кандидата (состояние, остаток, срок, крайняя дата) — PostgreSQL с `LIMIT` может вернуть строку, которую параллельная транзакция успела изменить. Если кандидат больше не подходит, подбор повторяется (до 3 раз), затем исход «нет абонемента». Дополнительно списание делает `UPDATE ... SET sessions_remaining = sessions_remaining - 1 WHERE id = :id AND (sessions_remaining IS NULL OR sessions_remaining > 0)` и считает 0 обновлённых строк конфликтом. Порядок блокировок — абонементы клиента по одному за раз, поэтому взаимных блокировок между обработчиками не возникает. Превью в `read/` не может импортировать домен, поэтому `DbSessionDebitPreviewView` повторяет тот же `WHERE`/`ORDER BY`. Расхождение ловит общий набор тестовых сценариев, который прогоняется на обеих реализациях.

Альтернатива — вызывать домен из маршрута превью для каждого участника. Отвергнута: N запросов и нарушение правил слоя `read/`.

Неоплаченные занятия — проекция `read/memberships/UnpaidSessionsView`: проведённые занятия, отметка «пришёл», нет действующего списания, фильтр по группам абонемента.

### 8. Фоновая обработка

`MembershipScheduler` по образцу `DomainEventWorker`: цикл раз в 15 минут. Один SQL выбирает абонементы, у которых по местной дате организации:
- `INACTIVE` + `ON_DATE` и `activate_on <= today` → активация «по дате»;
- `INACTIVE` + `ON_FIRST_VISIT` и `activation_deadline <= today` → активация «по крайней дате» с `startDate = deadline`;
- `ACTIVE` + `ON_EXPIRY` и `end_date < today` → закрытие «по сроку»;
- `ACTIVE`, `close_notified_at IS NULL` и (`MANUAL` и (`end_date < today` или остаток 0) или `ON_LAST_SESSION` и `end_date < today` и остаток > 0) → уведомление «к закрытию» и `close_notified_at = now()`. `ON_EXPIRY` уведомлений не получает: закроется по сроку.

Каждый абонемент — своя транзакция; повторная выборка того же абонемента ничего не делает, потому что состояние уже изменилось. Уведомление при обнулении остатка списанием отправляется сразу в транзакции списания по тому же правилу, а не ждёт цикла.

Абонемент, активированный задним числом сразу со «сроком вышел» (поздно проведённое первое посещение), отдельно не обрабатывается: следующий проход закроет его (`ON_EXPIRY`) или уведомит (`MANUAL`, `ON_LAST_SESSION` с остатком).

После системной активации (фоновой или первым посещением) в той же транзакции проверяются неоплаченные занятия и при необходимости создаётся задача.

### 9. Задача без автора, видимость неназначенных

`tasks.created_by` становится nullable, `null` = «Система». Задача создаётся без исполнителя, с `clientId`.
- `Task.createdBy: EmployeeId?`, `TaskDetailRow.createdBy: EmployeeId?`; в схемах `createdBy`/`createdByName` nullable; `DbTaskDetailView` — `asUuidOrNull`. Веб показывает «Система» при `createdBy == null`.
- `DbTask.requireEditable`: без `CAN_MANAGE_TASKS` `assignTo(employee)` разрешён, если `assigneeId == null` и `employee.id == ctx.employee.id` («взять себе»); остальные проверки без изменений.
- `DbTaskListView`: без `CAN_VIEW_ALL_TASKS` условие `(t.assignee_id = :me OR t.created_by = :me OR t.assignee_id IS NULL)`; при `onlyMine` — прежнее `(assignee_id = :me OR created_by = :me)`. Карточка задачи сейчас скоупится только по `orgId`, ограничения не меняются.
- Проверить остальные места, где `created_by` читается как не-null (`DbTasks`, аудит задач, уведомления о задачах). Альтернатива — автором ставить выдавшего абонемент: отвергнута, потому что он может быть удалён, а задачу создал не он.

### 10. Схема ответа

`MembershipSchema.state` — sealed: `Inactive(activationMode, activateOn?, deadline?)`, `Active(startDate, endDate, termPosition, sessionsRemaining?, endingSoon: List<EndingSoonReason>, closePending)`, `Closed(startDate, endDate, closedOn, reason)`, `Revoked`. Плюс `closeMode`, `groups: List<GroupRef>`. `MembershipStatus` удаляется.

Новые маршруты (идентификатор в теле): `memberships/history`, `memberships/close`, `memberships/revoke`, `memberships/unpaid-sessions`, `memberships/repay-debt`, `sessions/debit-preview`.

### 11. Надёжность доменных событий

`DomainEventWorker` меняется для всех обработчиков, не только абонементов:
- колонки `domain_events.next_attempt_at` и `failed_at`; выборка — `processed_at IS NULL AND failed_at IS NULL AND next_attempt_at <= now()`;
- после ошибки `retry_count + 1`, `next_attempt_at = now() + min(10 с · 2^retry, 1 ч)`; лимит 12 попыток (суммарно ~10 часов);
- после лимита — `failed_at = now()`, а не `processed_at`: событие не считается выполненным;
- admin-команда CLI `admin events retry-failed [--org <id>] [--event <id>]` сбрасывает `failed_at`, `retry_count`, `next_attempt_at`; описать в `docs/cli.md`.

Альтернатива — периодическая сверка всех проведённых занятий без списаний. Отвергнута: она не отличает «ещё не обработано» от «абонемента не было» и задним числом списывала бы с абонементов, выданных после занятия, хотя это решение администратора (погашение долга).

## Risks / Trade-offs

- [Превью и списание разойдутся] → общий набор сценариев подбора, прогоняемый на обеих реализациях (п. 7).
- [Списание асинхронно: сразу после проведения превью и остаток могут показывать старое] → веб после проведения инвалидирует запросы с небольшой задержкой; рассинхрон — секунды.
- [Фоновая активация с опозданием до 15 минут] → спека допускает до часа; при выдаче «с даты» не позже сегодня активация синхронная.
- [Абонемент «по истечении срока» закрывается раньше, чем проводится занятие последнего дня] → принятое правило (спека, «Режим закрытия задаётся при выдаче»): режим выбирает администратор; занятие становится долгом и гасится вручную с другого абонемента. Разрешать списание с закрытого абонемента отвергнуто — это ломает финальность «Закрыт».
- [Изменение `DomainEventWorker` влияет на все существующие обработчики] → поведение для успешных событий не меняется; тест воркера на задержку, лимит и `failed_at`.
- [Неназначенные задачи станут видны всем, включая созданные сотрудниками без исполнителя] → это и есть желаемое правило: задача без исполнителя — общая очередь.
- [Уникальный частичный индекс отклонит параллельное второе списание исключением] → обработчик сверки повторяется worker'ом и на второй попытке видит действующее списание.

## Migration Plan

Проект не в продакшене, реальных данных нет — миграция данных не делается и корректность исторических данных не обеспечивается.

- Этап 1: `DELETE FROM memberships`, затем новые колонки, nullable `start_date`/`end_date`, CHECK-ограничения, `membership_events`. Без вычисления состояний и режимов для старых строк.
- Этап 2: `membership_groups` без заполнения.
- Этап 3: `membership_debits`; прошлая посещаемость без списаний станет «неоплаченной» — это приемлемо.
- Этап 4 и п. 11: nullable `tasks.created_by`, колонки `domain_events`.

Откат: только через пересоздание БД стенда.

## Open Questions

- Тексты уведомления «к закрытию» и задачи «Проверить клиента» — уточняются в словарях при реализации.
- Интервал фоновой обработки (15 минут) можно вынести в `application.conf`, если понадобится.
