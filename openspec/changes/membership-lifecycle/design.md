## Context

- `memberships` (0059): `start_date`/`end_date NOT NULL`, `sessions_total`/`sessions_remaining`, без состояния, групп и режимов. `Membership` — интерфейс с полями и `save()` (только INSERT), `DbMemberships.new` вычисляет `endDate` от `startDate` и срока.
- Статус считается в `MembershipsRoutes.toSchema()` по `TimeZone.currentSystemDefault()`; `membershipEndingSoon` — в `web/src/clients/card/clientCardAlerts.ts`.
- Журнал посещаемости публикует `SessionCompleted` (итоговый состав) и `AttendanceChanged` (в том числе после проведения) через outbox: `DomainEventWorker` вызывает `DomainEventHandler.handle(orgId, event)` асинхронно, в своей транзакции, at-least-once.
- Есть `SystemRequestContext` для действий без сотрудника. Часовой пояс организации — `organizations.timezone`, в SQL уже используется `AT TIME ZONE o.timezone`.
- `tasks.created_by NOT NULL`; у клиента нет ответственного сотрудника. Уведомления сотрудникам — `domain/notifications`.
- Контракты для веба генерируются из `shared`, sealed-схемы поддерживаются (`NotificationsSchema`, `MessagingSchemas`).

## Goals / Non-Goals

**Goals:**
- Недопустимое действие непредставимо: метода нет у типа состояния.
- Выводимые свойства — чистые функции от абонемента и даты, тестируются без БД.
- Списания идемпотентны и не зависят от порядка обработки событий.
- Одно правило подбора абонемента для списания и для превью.

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

Новые колонки `memberships`: `state` (`INACTIVE | ACTIVE | CLOSED | REVOKED`), `activation_mode` (`IMMEDIATE | ON_DATE | ON_FIRST_VISIT`), `activate_on`, `activation_deadline`, `duration_value`, `duration_unit`, `close_mode` (`MANUAL | ON_LAST_SESSION | ON_EXPIRY`), `issued_on DATE`, `close_requested_at`, `closed_on`, `close_reason`. `start_date`/`end_date` становятся nullable. CHECK-ограничения: у `ACTIVE`/`CLOSED` даты заданы; у `ON_FIRST_VISIT`, `INACTIVE` дат нет; `ON_LAST_SESSION` требует `sessions_total`.

`DbMemberships` маппит строку в нужный подтип. Таблицы:
- `membership_groups (membership_id, group_id)`;
- `membership_events (id, org_id, membership_id, occurred_at, actor_id NULL, kind, payload JSONB)` — история; `actor_id NULL` = система;
- `membership_debits (id, org_id, membership_id, client_id, session_id, kind REGULAR|DEBT, created_at, reversed_at NULL)` с уникальным частичным индексом `(session_id, client_id) WHERE reversed_at IS NULL` — не больше одного действующего списания за участие.

`sessions_remaining` остаётся колонкой и меняется в той же транзакции, что и списание. Конкурентность — `SELECT ... FOR UPDATE` абонемента в `byId` для команд.

### 3. История — своя таблица, не `AuditLog`

`AuditLog` best-effort и не транзакционен (CLAUDE.md, «Known Constraints» п. 5), а история видна пользователю и должна совпадать с состоянием. Поэтому событие истории пишет сам метод перехода в той же транзакции. Аудит (`AuditMemberships`) остаётся как есть для операций сотрудников.

### 4. «Сегодня» — по часовому поясу организации

`OrgCalendar` с методом `today()`: часовой пояс из `organizations`, время из `kotlin.time.Clock` (подменяется в тестах). Домен получает дату параметром (`statusAt(date)`, `activate(startDate)`), а не читает часы сам. Маршрут списка абонементов берёт `today()` у `OrgCalendar`, фоновая обработка — из SQL `(now() AT TIME ZONE o.timezone)::date`.

### 5. Контекст: `RequestContext`, автор — из контекста

Методы абонемента принимают `context(ctx: RequestContext, ...)`. Автор события истории: `EmployeeRequestContext.employee.id`, для `SystemRequestContext` — `null`. Обработчики событий и фоновая обработка работают в `SystemRequestContext(orgId)`.

### 6. Списания — сверка по текущему состоянию, а не дельта по событию

Обработчики `SessionCompleted` и `AttendanceChanged` для каждой пары (занятие, клиент) выполняют одну операцию: «должно быть списание» = занятие проведено и отметка сейчас «пришёл». Состояние читается из БД, а не из события:
- должно быть, нет действующего → подобрать абонемент и списать;
- не должно быть, есть действующее → вернуть (`reverse`);
- иначе ничего.

Так повторная доставка, обратный порядок событий и `AttendanceChanged` по открытому журналу безопасны. Синхронное списание в транзакции проведения отвергнуто: журнал стал бы зависеть от абонементов, а ошибка подбора ломала бы проведение.

### 7. Правило подбора — один SQL в домене и его копия в превью

`Memberships.debitCandidate(clientId, groupId, sessionDate)` — один SQL с порядком из спеки. Превью в `read/` не может импортировать домен, поэтому `DbSessionDebitPreviewView` повторяет тот же `WHERE`/`ORDER BY`. Расхождение ловит общий набор тестовых сценариев, который прогоняется на обеих реализациях.

Альтернатива — вызывать домен из маршрута превью для каждого участника. Отвергнута: N запросов и нарушение правил слоя `read/`.

Неоплаченные занятия — проекция `read/memberships/UnpaidSessionsView`: проведённые занятия, отметка «пришёл», нет действующего списания, фильтр по группам абонемента.

### 8. Фоновая обработка

`MembershipScheduler` по образцу `DomainEventWorker`: цикл раз в 15 минут. Один SQL выбирает абонементы, у которых по местной дате организации:
- `INACTIVE` + `ON_DATE` и `activate_on <= today` → активация «по дате»;
- `INACTIVE` + `ON_FIRST_VISIT` и `activation_deadline <= today` → активация «по крайней дате» с `startDate = deadline`;
- `ACTIVE` + `ON_EXPIRY` и `end_date < today` → закрытие «по сроку»;
- `ACTIVE` + `MANUAL`, `close_requested_at IS NULL` и (`end_date < today` или остаток 0) → пометка «к закрытию» и уведомление.

Каждый абонемент — своя транзакция; повторная выборка того же абонемента ничего не делает, потому что состояние уже изменилось. Пометка «к закрытию» при обнулении остатка списанием выполняется сразу в транзакции списания, а не ждёт цикла.

После системной активации (фоновой или первым посещением) в той же транзакции проверяются неоплаченные занятия и при необходимости создаётся задача.

### 9. Задача без автора

`tasks.created_by` становится nullable, `null` = «Система». Задача создаётся без исполнителя, с `clientId`. Веб показывает автора «Система». Альтернатива — автором ставить выдавшего абонемент: отвергнута, потому что он может быть удалён, а задачу создал не он.

### 10. Схема ответа

`MembershipSchema.state` — sealed: `Inactive(activationMode, activateOn?, deadline?)`, `Active(startDate, endDate, termPosition, sessionsRemaining?, endingSoon: List<EndingSoonReason>, closePending)`, `Closed(startDate, endDate, closedOn, reason)`, `Revoked`. Плюс `closeMode`, `groups: List<GroupRef>`. `MembershipStatus` удаляется.

Новые маршруты (идентификатор в теле): `memberships/history`, `memberships/close`, `memberships/revoke`, `memberships/unpaid-sessions`, `memberships/repay-debt`, `sessions/debit-preview`.

## Risks / Trade-offs

- [Превью и списание разойдутся] → общий набор сценариев подбора, прогоняемый на обеих реализациях (п. 7).
- [Списание асинхронно: сразу после проведения превью и остаток могут показывать старое] → веб после проведения инвалидирует запросы с небольшой задержкой; рассинхрон — секунды.
- [Фоновая активация с опозданием до 15 минут] → спека допускает до часа; при выдаче «с даты» не позже сегодня активация синхронная.
- [Миграция назначит группы, в которые клиент записан сейчас, а не на дату выдачи] → приемлемо: старые абонементы до этого не списывались вовсе; администратор может поправить группы позже (отдельная задача).
- [Уникальный частичный индекс отклонит параллельное второе списание исключением] → обработчик сверки повторяется worker'ом и на второй попытке видит действующее списание.
- [`created_by NULL` у задач затронет проекции задач] → проверить `read/tasks` и веб на nullable автора в этапе 4.

## Migration Plan

Liquibase, этап 1:
1. Новые колонки с безопасными значениями: `activation_mode = 'ON_DATE'`, `activate_on = start_date`, `close_mode = 'MANUAL'`, `issued_on = created_at::date` (по часовому поясу организации), `duration_*` — из тарифа, если есть, иначе вычисляется из разницы дат в днях.
2. `state = 'ACTIVE'`, если `start_date <= сегодня` организации, иначе `'INACTIVE'`.
3. `start_date`/`end_date` → nullable, CHECK-ограничения.

Этап 2: `membership_groups` заполняется из текущих записей клиента в группы.

Откат: колонки и таблицы добавочные; откат кода до этапа 1 работает с теми же `start_date`/`end_date`, пока среди абонементов нет `ON_FIRST_VISIT` без дат.

## Open Questions

- Тексты уведомления «к закрытию» и задачи «Проверить клиента» — уточняются в словарях при реализации.
- Интервал фоновой обработки (15 минут) можно вынести в `application.conf`, если понадобится.
