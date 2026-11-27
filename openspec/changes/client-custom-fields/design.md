# Design

## Context

- Определения хранятся в `custom_field_definitions` (миграция `0042`) с уникальностью `(org_id, entity_type, field_key)` и `id UUID DEFAULT uuidv7()`; значения — JSONB-массив `clients.custom_fields` (миграция `0044`).
- `DbCustomFieldDefinitions.saveAll` делает `DELETE` всего набора и `INSERT` заново, поэтому `id` поля меняется при каждом сохранении. API наружу `id` не отдаёт: `CustomFieldDefinition` (shared) идентифицируется только ключом.
- `Di.kt` создаёт `DbCustomFieldDefinitions()` без декоратора. Остальные справочники аудируются декораторами `AuditXxx(delegate, audit)` (`AuditLeadSources`, `AuditAttendanceLabels`).
- Журнал (`DbActivityLogView`) достаёт название субъекта `LEFT JOIN`'ом по `entity_type` + `entity_id`; для удалённой строки название `NULL` → «недоступен». Web (`AuditLogPage.tsx`) строит ссылку по `subject.type`.
- Значения полей у клиента уже аудируются снимком клиента (`AuditClient`), это изменение их не касается.

## Goals / Non-Goals

**Goals:**
- Событие журнала на каждое добавленное, изменённое и удалённое определение со стабильным субъектом.
- Спецификация текущего поведения без изменения API и web-форм.

**Non-Goals:**
- Аудит значений полей у клиента (уже есть).
- Новые права, ограничение `entityType`, работа `isSearchable` / `isSortable` — см. Open Questions.

## Decisions

### 1. Upsert по ключу вместо пересоздания набора
`saveAll` удаляет строки, чьих ключей нет в запросе (`field_key <> ALL(:keys)`), и вставляет остальные через `INSERT … ON CONFLICT (org_id, entity_type, field_key) DO UPDATE`, обновляя название, тип, конфиг, признаки и `display_order`. Ключ неизменяем, поэтому ключ и есть естественная идентичность поля, а `id` сохраняется.
*Альтернатива:* оставить пересоздание и писать в событие `entity_id = NULL` или id организации — журнал показал бы событие без субъекта и без перехода, а история одного поля распалась бы.

### 2. Декоратор `AuditCustomFieldDefinitions`
По образцу `AuditLeadSources`: `class AuditCustomFieldDefinitions(delegate, audit) : CustomFieldDefinitions by delegate`, переопределяет только `saveAll`. Перед вызовом делегата читает сохранённый набор, после — новый, сравнивает по ключу:
- ключа не было → `logCreate`;
- ключ был, изменилось определение (`equals` data-класса) или позиция → `logUpdate`;
- ключ пропал → `logDelete` со снимком до удаления.
Тип сущности журнала — `custom_field`; `data` — JSON `{entityType, position, definition}` с `definition`, сериализованным `appJson` как в API.
Чтение до и после идёт в той же транзакции, поэтому ошибка сохранения откатывает и события.
*Альтернатива:* логировать внутри `DbCustomFieldDefinitions` — нарушает принятое разделение «репозиторий / аудит-декоратор».

### 3. Доступ к идентификатору и позиции
Декоратору нужны `id` и позиция, которых нет в `CustomFieldDefinition`. В интерфейс `CustomFieldDefinitions` добавляется запрос `stored(entityType): List<StoredCustomFieldDefinition>` — доменный тип `(id: CustomFieldDefinitionId, position: Int, definition: CustomFieldDefinition)`. Новый `CustomFieldDefinitionId` — value class в `core/entityids` по образцу `MembershipId`. `all()` остаётся как есть для остальных потребителей.
*Альтернатива:* добавить `id` в shared-схему `CustomFieldDefinition` — меняет контракт API и web без пользы для экрана.

### 4. Субъект в журнале
`DbActivityLogView`: `WHEN 'custom_field' THEN cfd.label` и `LEFT JOIN custom_field_definitions cfd ON a.entity_type = 'custom_field' AND cfd.id = a.entity_id AND cfd.org_id = a.org_id`. `Messages.auditEntity`: «дополнительного поля» / «custom field». Web: `case "custom_field"` → `Link to="/settings" search={{ panel: "client-additional-attributes" }}`.

## Risks / Trade-offs

- [Старые события] До выката событий по полям нет; ретроспективно не восстанавливаются → принимается.
- [Гонка двух одновременных сохранений] Как и раньше, последнее сохранение побеждает; diff декоратора может показать не все промежуточные изменения → принимается, справочник меняется редко.
- [Дубли ключей в запросе] Сейчас дают нарушение уникальности (HTTP 500); при построчном upsert второй дубль молча перезаписал бы первый → `saveAll` явно отклоняет запрос с повтором ключа ошибкой `CUSTOM_FIELD_DUPLICATE_KEY`. Web дубль не отправляет.

## Migration Plan

Миграции схемы не нужны. Выкат обычный; откат — возврат кода, данные совместимы (строки и `id` те же).

## Open Questions

- Нужна ли проверка права на изменение набора полей (сейчас `/custom-fields/save` доступен любому сотруднику)? Решается отдельным change вместе с правами на прочие справочники.
- `entityType` принимается любой строкой, используется только `CLIENT`. Ограничивать ли — отдельно.
- Флаги `isSearchable` / `isSortable` сохраняются и возвращаются, но поиск и сортировка списка клиентов их не используют.
