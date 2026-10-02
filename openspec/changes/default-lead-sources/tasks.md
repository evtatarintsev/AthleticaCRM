## 1. Пояснение источника на сервере

- [ ] 1.1 Миграция `0069-lead-source-description.sql` (`ALTER TABLE lead_sources ADD COLUMN description TEXT`), подключить в changelog; проверка — сервер-тесты на TestContainers поднимаются без ошибок Liquibase
- [ ] 1.2 `LeadSourceDescription` (smart constructor: trim, пусто → `null`, > 500 → `LEAD_SOURCE_DESCRIPTION_TOO_LONG`) + сообщение в `Messages`; проверка — unit-тест на границы 500/501, пробелы и пустую строку
- [ ] 1.3 Поле `description` в `LeadSource`, `LeadSources.new`, `withNew`, `DbLeadSource.save()` (INSERT и `ON CONFLICT`), чтение в `list` / `byIds`; проверка — DB-тест `DbLeadSourcesTest`: создание с пояснением и без, обновление пояснения, очистка
- [ ] 1.4 Пояснение в `LeadSourceAuditData` (`AuditLeadSource`); проверка — тест аудита сохраняет пояснение в данных события
- [ ] 1.5 `ImportClientsCommit` создаёт источники с `description = null`; проверка — `ImportClientsCommitTest` зелёный

## 2. API

- [ ] 2.1 `description: String? = null` в `CreateLeadSourceRequest`, `UpdateLeadSourceRequest`, `LeadSourceDetailResponse`; проверка — `./gradlew compileKotlin` по всем модулям, включая `composeApp`
- [ ] 2.2 `LeadSourcesRoutes`: `create` / `update` разбирают пояснение через `LeadSourceDescription.from`, `list` отдаёт его; проверка — route-тест: создание и обновление с пояснением, 400 на 501 символ, пробельное пояснение возвращается как отсутствующее

## 3. Стартовый набор при регистрации

- [ ] 3.1 Ключи `Messages` для 10 названий и 10 пояснений (RU/EN) строго по таблице из спеки
- [ ] 3.2 `DefaultLeadSources.createFor(orgId, lang)` и вызов в `signUp` после `DefaultAttendanceLabels.createFor`; обновить KDoc `signUp`; проверка — в `SignUpTest`: при RU и EN создаются ровно 10 источников с ожидаемыми названиями и пояснениями, у двух организаций наборы независимы

## 4. Веб

- [ ] 4.1 `cd web && npm run contracts`, закоммитить `web/src/api/generated/`; проверка — `npm run check` проходит typecheck
- [ ] 4.2 `DirectoryItem.description`, `DirectoryDefinition.describable`; у `leadSources` — `describable: true` и передача пояснения в `create` / `update`, у остальных справочников запросы без изменений; проверка — typecheck
- [ ] 4.3 `DirectorySheet`: пояснение под названием в строке таблицы при `describable`; проверка — тест в `DirectorySheet.test.tsx`: пояснение видно у источника и отсутствует у справочника без флага
- [ ] 4.4 `DirectoryItemSheet`: необязательное многострочное поле «Пояснение» (trim, max 500, пусто → `null`) при `describable`; строки в `i18n/ru.ts` / `en.ts`; проверка — тест: создание с пояснением отправляет его, очистка отправляет `null`, 501 символ показывает ошибку и не отправляет запрос
- [ ] 4.5 `ClientFormPage`: `hint` поля источника — пояснение выбранного источника; проверка — тест: выбор источника с пояснением показывает подсказку, «не указан» и источник без пояснения — нет

## 5. Финальная проверка

- [ ] 5.1 `./gradlew build` и `./gradlew ktlintFormat` без ошибок
- [ ] 5.2 В `web/`: `npm run format` и `npm run check` без ошибок
- [ ] 5.3 Вручную в dev: регистрация новой организации → в настройках 10 источников с пояснениями; правка пояснения отражается в подсказке формы клиента
