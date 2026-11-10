## Context

- «Журнал закрыт» = занятие проведено (`sessions.status = 'completed'`, `Session.complete()`). Отдельного статуса «ожидает закрытия» нет, и в этом изменении он не вводится: незакрытость выводится из `status = 'scheduled'` и времени окончания.
- `sessions.date` + `end_time` — местное время организации (`organizations.timezone`, IANA). Филиала у занятия нет — он берётся у группы (`groups.branch_id`), как в `DbScheduleView`.
- Системные задачи уже есть: `Tasks.new` в `SystemRequestContext` пишет `created_by = NULL` («Система»), задачи без исполнителя видны всем (спека `tasks`). Пример — «Проверить клиента» в `MembershipScheduler`.
- Фоновые циклы — корутины в `Application.module()` (`MembershipScheduler.run()`: SQL выбирает кандидатов по всем организациям, каждый обрабатывается в своей транзакции через `SystemContexts.forOrg`). Сервер может работать в нескольких экземплярах.

## Goals / Non-Goals

**Goals:**
- Идемпотентное создание задачи — устойчиво к повторным проходам и параллельным экземплярам.
- Настройки — отдельный агрегат, не трогающий кэшируемый `Organization`.
- Виджет — обычная read-проекция по шаблону `read/`.

**Non-Goals:**
- Статусная модель занятия из #102, пинги, эскалация, автозакрытие.
- Связь задачи с занятием в модели задач (ссылка на занятие в карточке задачи) — только текст описания.
- Права на изменение настроек: как у основных настроек организации, без отдельного права.

## Decisions

### Настройки — отдельная таблица `attendance_control_settings`, отсутствие строки = значения по умолчанию

Столбцы `org_id` (PK), `overdue_task_enabled`, `overdue_task_delay_minutes` (CHECK 15..10080), `updated_at`. Чтение без строки возвращает дефолты (включено, 120), сохранение — upsert.
Домен — `domain/attendancecontrol/`: `AttendanceControls.current()` и агрегат `AttendanceControl` (`withNew`, `save`) по образцу `Organizations`/`Organization`. Окно — smart constructor `JournalCloseDelay` (value class, `from(minutes): Either`, ошибка `INVALID_JOURNAL_CLOSE_DELAY`), как велит CLAUDE.md для диапазонов.

*Альтернативы:* столбцы в `organizations` — затрагивают агрегат с кэшем `LocMemCachedOrganizations` и раздувают его; запись строки при регистрации организации — требует миграции данных для существующих организаций и правки signup ради значений, которые и так дефолтные.

Дефолты живут в Kotlin (`AttendanceControl.DEFAULT_OVERDUE_TASK_ENABLED`, `JournalCloseDelay.DEFAULT`) и передаются в SQL планировщика параметрами, чтобы не дублировать их литералами в запросах.

### Идемпотентность — таблица-гард `session_overdue_tasks` с PK по занятию

`session_overdue_tasks(session_id PK → sessions ON DELETE CASCADE, org_id, task_id → tasks ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED, created_at)`.
В транзакции на одно занятие: сгенерировать `TaskId` → `INSERT ... SELECT FROM sessions WHERE id = :id AND status = 'scheduled' ON CONFLICT (session_id) DO NOTHING` → если вставлена 0 строк, ничего не делать; иначе `tasks.new(taskId, ...)`. Отложенный FK позволяет занять занятие до вставки задачи; повторная проверка статуса в том же `INSERT` закрывает гонку с проведением занятия между выборкой и обработкой.

Удалённая или выполненная задача не порождает новую: строка гарда остаётся (у удалённой — `task_id = NULL`).

*Альтернативы:* столбец `sessions.overdue_task_id` — расширяет агрегат занятия и рискует задеть предикат «нетронутых» занятий сверки расписания (`UntouchedSessions`); advisory-lock на проход — не защищает от повторов между проходами.

### Планировщик `OverdueJournalScheduler` в `usecases/sessions/`

Цикл как у `MembershipScheduler`: `run()` раз в 5 минут вызывает `processDue()`, ошибки логируются, цикл не падает; `Clock` и интервал — параметры конструктора для тестов. Выборка по всем организациям одним SQL:

- `s.status = 'scheduled'`, `NOT EXISTS` в `session_overdue_tasks`, `COALESCE(acs.overdue_task_enabled, :defaultEnabled)`;
- `deadline = ((s.date + s.end_time) AT TIME ZONE o.timezone) + make_interval(mins => COALESCE(acs.overdue_task_delay_minutes, :defaultDelay))`;
- `deadline <= :now AND deadline > :now - interval '1 day'` — окно «только недавние» (спека: давние незакрытые занятия задачу не получают);
- грубый фильтр `s.date >= :fromDate` (`now − (7 суток + 1 сутки + 2 суток запаса на пояс)`) по частичному индексу `sessions(date) WHERE status = 'scheduled'`, чтобы не сканировать всю историю.

Вместе с id выбираются название группы, дата и время — для текста задачи. Каждое занятие — своя транзакция в `SystemContexts.forOrg(orgId)`; язык текста — язык системного контекста (RU). Тексты — `Messages.OverdueJournalTaskTitle` / `OverdueJournalTaskDescription`, дата — `LocalDate.localized`.

*Альтернатива:* реагировать на событие окончания занятия — такого события нет (окончание по расписанию — это просто время), отложенных событий шина не поддерживает.

### Виджет — проекция `read/home/UnclosedSessionsView` и `GET /home/unclosed-sessions`

`UnclosedSessionsQuery(now, limit)`: `now` передаёт маршрут (`Clock.System.now()`), тест — фиксированный момент; `limit` нормализуется в маршруте (`coerceIn(1, 50)`). Один SQL на страницу и отдельный `COUNT` для `total`, скоуп по `ctx.orgId` и `g.branch_id = ctx.branchId`. Ответ — `UnclosedSessionsResponse` из `api.schemas.home`. Окна «недавних» здесь нет — список показывает весь накопленный долг.

`DashboardWidget.UnclosedSessions(id, title, limit = 10)` с дискриминатором `unclosedSessions` добавляется в `DashboardSettings.default()`; `withDefaults()` (сервер) и `withDashboardDefaults()` (веб) сами добавят его существующим пользователям.

### Маршруты настроек — под `/org`

`GET /org/attendance-control` и `POST /org/attendance-control/update` в `OrgRoutes` рядом с основными настройками (без path-переменных, по правилам REST проекта). Схемы — `api/schemas/attendancecontrol/`: `AttendanceControlSettingsResponse`, `UpdateAttendanceControlSettingsRequest` (минуты — `Int`, проверка — в `JournalCloseDelay`).

### Веб: панель и виджет

- `settingsSearch.ts`: панель `attendance-control`; `SettingsPage`: пункт в разделе «Занятия и абонементы» (иконка `AlarmClockIcon`), лист `settings/attendanceControl/AttendanceControlSheet.tsx` по образцу `OrgSettingsSheet` (`EditSheet`, TanStack Form, `CheckboxField` + `SelectField`). Набор вариантов времени — фиксированный в клиенте; несовпадающее сохранённое значение добавляется вариантом «N мин».
- `home/UnclosedSessionsWidgetCard.tsx` по образцу `SessionsWidgetCard`; тип в `WIDGET_TYPES`, `defaultWidgetOf`, `defaultTitleKey`; настройки лимита — в `WidgetSettingsButton` как у должников.
- Строки — в `i18n/ru.ts` и `en.ts`; контракты — `npm run contracts`.

## Risks / Trade-offs

- [Первый запуск: у всех организаций задача включена по умолчанию] → окно «только недавние» ограничивает разовый всплеск занятиями за последние сутки.
- [Простой сервиса дольше суток] → задачи за пропущенное время не создаются; незакрытые занятия всё равно видны в виджете. Принимаем: лучше пропуск, чем лавина задач.
- [Неотменённые «мёртвые» занятия (группа фактически не занимается)] → будут порождать задачи; это и есть сигнал навести порядок в расписании.
- [Изменение окна на большее после создания задачи] → уже созданная задача не отзывается. Принимаем.
- [Задача не связана с занятием структурно] → ссылка только в тексте; связь «задача → занятие» — отдельное изменение.
- [Параллельные экземпляры] → PK гарда + `ON CONFLICT DO NOTHING`; дубль задачи невозможен.

## Migration Plan

Liquibase-миграция `0081-attendance-control.sql` (две таблицы и индекс) применяется при старте; данных не переносит. Откат — удалить таблицы и индекс; созданные задачи остаются обычными системными задачами.
