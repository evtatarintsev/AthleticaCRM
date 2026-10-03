## 1. Пояснение источника на сервере

- [x] 1.1 Миграция `0069-lead-source-description.sql` (`ALTER TABLE lead_sources ADD COLUMN description TEXT NOT NULL DEFAULT ''`), подключить в changelog; проверка — сервер-тесты на TestContainers поднимаются без ошибок Liquibase
- [x] 1.2 `LeadSourceDescription` (smart constructor: trim, > 500 → `LEAD_SOURCE_DESCRIPTION_TOO_LONG`, константа `EMPTY`) + сообщение в `Messages`; проверка — unit-тест на границы 500/501, пробелы и пустую строку
- [x] 1.3 Поле `description` в `LeadSource`, `LeadSources.new`, `withNew`, `DbLeadSource.save()` (INSERT и `ON CONFLICT`), чтение в `list` / `byIds`; проверка — DB-тест `DbLeadSourcesTest`: создание с пояснением и с пустым, обновление пояснения, очистка до пустого
- [x] 1.4 Пояснение в `LeadSourceAuditData` (`AuditLeadSource`); проверка — тест аудита сохраняет пояснение в данных события
- [x] 1.5 `ImportClientsCommit` создаёт источники с `LeadSourceDescription.EMPTY`; проверка — `ImportClientsCommitTest` зелёный

## 2. API

- [x] 2.1 `description: String = ""` в `CreateLeadSourceRequest`, `UpdateLeadSourceRequest`, `description: String` в `LeadSourceDetailResponse`; проверка — `./gradlew compileKotlin` по всем модулям, включая `composeApp`
- [x] 2.2 `LeadSourcesRoutes`: `create` / `update` разбирают пояснение через `LeadSourceDescription.from`, `list` отдаёт его; проверка — route-тест: создание и обновление с пояснением, 400 на 501 символ, пробельное пояснение возвращается пустой строкой, запрос без поля создаёт источник с пустым пояснением

## 3. Стартовый набор при регистрации

- [x] 3.1 Ключи `Messages` для 10 названий и 10 пояснений (RU/EN) строго по таблице из спеки
- [x] 3.2 `DefaultLeadSources.createFor(orgId, lang)` и вызов в `signUp` после `DefaultAttendanceLabels.createFor`; обновить KDoc `signUp`; проверка — в `SignUpTest`: при RU и EN создаются ровно 10 источников с ожидаемыми названиями и пояснениями, у двух организаций наборы независимы

## 4. Веб

- [x] 4.1 `cd web && npm run contracts`, закоммитить `web/src/api/generated/`; проверка — `npm run check` проходит typecheck
- [x] 4.2 `DirectoryItem.description`, `DirectoryDefinition.describable`; у `leadSources` — `describable: true` и передача пояснения в `create` / `update`, у остальных справочников запросы без изменений; проверка — typecheck
- [x] 4.3 `DirectorySheet`: пояснение под названием в строке таблицы при `describable`; проверка — тест в `DirectorySheet.test.tsx`: пояснение видно у источника и отсутствует у справочника без флага
- [x] 4.4 `DirectoryItemSheet`: необязательное многострочное поле «Пояснение» (trim, max 500, пусто → `""`) при `describable`; строки в `i18n/ru.ts` / `en.ts`; проверка — тест: создание с пояснением отправляет его, очистка отправляет `""`, 501 символ показывает ошибку и не отправляет запрос
- [x] 4.5 `ClientFormPage`: `hint` поля источника — пояснение выбранного источника; проверка — тест: выбор источника с пояснением показывает подсказку, «не указан» и источник с пустым пояснением — нет

## 5. Финальная проверка

- [x] 5.1 `./gradlew build` и `./gradlew ktlintFormat` без ошибок
- [x] 5.2 В `web/`: `npm run format` и `npm run check` без ошибок
- [x] 5.3 Вручную в dev: регистрация новой организации → в настройках 10 источников с пояснениями; правка пояснения отражается в подсказке формы клиента
