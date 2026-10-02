## Context

Мотивация — в proposal.md, требования — в `specs/lead-sources/spec.md`.

Текущее состояние:
- `lead_sources (id, org_id, name, created_at)`, уникальность `(org_id, name)`; агрегат `domain/leadSource` (`LeadSources` / `LeadSource` + `Audit*`-декораторы), маршруты `routes/LeadSourcesRoutes.kt`.
- `signUp` создаёт организацию прямыми SQL-вставками и уже заполняет справочник меток посещаемости через `DefaultAttendanceLabels.createFor(orgId, lang)` — названия берутся из `Messages` и локализуются по языку регистрации.
- В вебе источники, залы, дисциплины и филиалы показываются одним обобщённым `DirectorySheet` (поле «Название»), описание справочника — `DirectoryDefinition` в `settings/directories.ts`.
- В форме клиента источник выбирается нативным `SelectField`, у которого уже есть `hint` под полем.

## Goals / Non-Goals

**Goals:**
- Заполнение справочника при регистрации тем же способом, что и метки посещаемости.
- Инвариант пояснения (обрезано, непустое, ≤ 500 символов) проверяется в одном месте — при создании значения.
- Механизм пояснения в вебе переиспользуем для других справочников, но включается только у источников.

**Non-Goals:**
- Порядок источников в справочнике: остаётся сортировка по названию.
- Поддержка пояснения в desktop-клиенте.

## Decisions

### 1. Заполнение — объект `DefaultLeadSources` рядом с агрегатом

`domain/leadSource/DefaultLeadSources.createFor(orgId, lang)` в `context(tr: Transaction)`, по образцу `DefaultAttendanceLabels`: список пар ключей `Messages` (название, пояснение), вставка прямым SQL. Вызов — в `signUp` сразу после `DefaultAttendanceLabels.createFor`, внутри той же транзакции.

Почему не через `LeadSources.new(...).save()`: репозиторий требует `EmployeeRequestContext`, которого при регистрации ещё нет; кроме того, аудит предустановленных значений не нужен — так же, как для меток. Альтернатива — Liquibase-сид — отпадает: набор зависит от языка регистрации и нужен только новым организациям.

Ключи `Messages`: по два на источник — `DefaultLeadSourceReferral` / `DefaultLeadSourceReferralDescription` и т. д., RU + EN.

### 2. Пояснение — smart constructor `LeadSourceDescription`

`value class LeadSourceDescription private constructor(val value: String)` в `domain/leadSource` с фабрикой `from(raw: String): Either<DomainError, LeadSourceDescription?>`: обрезает пробелы, пустую строку превращает в `null`, длину > 500 отклоняет ошибкой `LEAD_SOURCE_DESCRIPTION_TOO_LONG` (локализованное сообщение в `Messages`, HTTP 400).

- Домен: `LeadSource.description: LeadSourceDescription?`; `LeadSources.new(id, name, description)`; `withNew(name, description)`. `AuditLeadSource` пишет пояснение в `LeadSourceAuditData`.
- Схемы `shared`: `description: String? = null` в `CreateLeadSourceRequest`, `UpdateLeadSourceRequest`, `LeadSourceDetailResponse`. Схема остаётся `String?`, а не value-классом: нормализация «пусто → отсутствует» — работа routes, а кастомный сериализатор не может превратить строку в `null`.
- Маршруты `create` / `update` разбирают `request.description` через `LeadSourceDescription.from(...).bind()`.
- `ImportClientsCommit` создаёт источники с `description = null`.

Альтернатива — проверка длины в маршруте строкой — отвергнута правилом проекта «Parse, don't validate».

### 3. БД — `description TEXT NULL`

Миграция `0069-lead-source-description.sql`: `ALTER TABLE lead_sources ADD COLUMN description TEXT`. Ограничение длины держит `LeadSourceDescription`; дублировать его `CHECK`-ом не стали, чтобы лимит менялся в одном месте. `DbLeadSource.save()` пишет пояснение и в `INSERT`, и в ветке `ON CONFLICT DO UPDATE`; `list` / `byIds` читают его.

### 4. Веб — флаг `describable` в `DirectoryDefinition`

- `DirectoryItem` получает `description?: string | null`; `DirectoryDefinition` — `describable?: boolean`. У источников `describable: true`, их `create` / `update` передают пояснение; у залов, дисциплин и филиалов `create` / `update` по-прежнему отправляют только `id` и `name`.
- `DirectorySheet`: при `describable` в ячейке под названием выводится пояснение (`text-muted-foreground`, обрезка в одну-две строки). Поиск — по-прежнему по названию.
- `DirectoryItemSheet`: при `describable` второе поле «Пояснение» — многострочное, необязательное, zod `trim().max(500)`; пустое отправляется как `null`.
- `ClientFormPage`: `hint` у `SelectField` источника = пояснение выбранного источника (или нет подсказки).

Альтернатива — отдельная панель только для источников — отвергнута: дублирует таблицу, выбор, удаление и вложенную панель; пояснение, вероятно, понадобится и другим справочникам.

Альтернатива для формы клиента — пояснение вторичной строкой внутри выпадающего списка — невозможна без замены нативного `<select>` на кастомный комбобокс; подсказка под полем даёт тот же эффект в момент выбора без нового компонента.

## Risks / Trade-offs

- [Desktop-клиент отправляет `update` без пояснения → пояснение стирается] → принято: desktop не поддерживается. Схема с `= null` по умолчанию сохраняет компиляцию `composeApp`.
- [«Другое» при сортировке по названию оказывается в середине списка] → принято на этом этапе; см. Open Questions.
- [CSV-импорт по совпадению имени теперь чаще попадает в предустановленные источники] → желаемое поведение: значения «Рекомендация», «Сайт» из выгрузок старой CRM сопоставятся без создания дублей.

## Migration Plan

Миграция только добавляет nullable-колонку — существующие строки получают `NULL`, откат не требует переноса данных. Существующие организации не меняются. Веб и сервер выкатываются вместе (общий образ); старый веб без поля `description` продолжает работать, кроме сброса пояснения при переименовании — окно короткое.

## Open Questions

- Нужен ли ручной порядок источников (как `position` у меток), чтобы «Другое» стояло последним? Можно добавить отдельно, не затрагивая этот change.
