# Tasks

## 1. Стабильные идентификаторы определений

- [x] 1.1 Добавить `CustomFieldDefinitionId` в `shared/.../core/entityids/` по образцу `MembershipId`
- [x] 1.2 Добавить доменный тип `StoredCustomFieldDefinition(id, position, definition)` и запрос `stored(entityType)` в `CustomFieldDefinitions` с реализацией в `DbCustomFieldDefinitions` (один SQL, скоуп по `ctx.orgId`)
- [x] 1.3 Переписать `DbCustomFieldDefinitions.saveAll`: отклонять повтор ключа (`CUSTOM_FIELD_DUPLICATE_KEY`), удалять только отсутствующие ключи, остальные — `INSERT … ON CONFLICT (org_id, entity_type, field_key) DO UPDATE`
- [x] 1.4 DB-тест `server/src/test/.../domain/customfields/`: `id` сохраняется при переименовании и смене порядка, удалённые ключи удаляются, дубль ключа отклоняется, некорректное определение не меняет набор

## 2. Аудит изменений определений

- [x] 2.1 Создать `domain/customfields/AuditCustomFieldDefinitions` (декоратор, переопределяет только `saveAll`): diff по ключу до/после, `logCreate` / `logUpdate` / `logDelete` с типом `custom_field` и снимком `{entityType, position, definition}`
- [x] 2.2 Подключить декоратор в `Di.kt` вместо голого `DbCustomFieldDefinitions()`
- [x] 2.3 Тест: добавление → один `CREATE`; переименование + удаление → `UPDATE` и `DELETE`; сохранение без изменений и ошибочное сохранение → событий нет; только смена порядка → `UPDATE` у сдвинутых полей

## 3. Субъект в журнале действий

- [x] 3.1 `DbActivityLogView`: название субъекта `custom_field` из `custom_field_definitions.label` с `LEFT JOIN` по `id` и `org_id`
- [x] 3.2 `Messages.auditEntity`: «дополнительного поля» / «custom field»
- [x] 3.3 Тест проекции журнала: существующее поле показывает название, удалённое — недоступный субъект
- [x] 3.4 Web `AuditLogPage.tsx`: `case "custom_field"` — ссылка на `/settings?panel=client-additional-attributes`; тест на ссылку

## 4. Проверки

- [x] 4.1 `./gradlew build` и `./gradlew ktlintFormat`
- [x] 4.2 В `web/`: `npm run format` и `npm run check`
- [x] 4.3 Убедиться, что `npm run contracts` не даёт изменений в `web/src/api/generated/` (API не менялся)
