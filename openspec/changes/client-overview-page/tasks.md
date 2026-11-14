## 1. Server: разделы карточки клиента

- [ ] 1.1 Liquibase-миграция: таблица разделов карточки (строка на организацию: `tasks`, `notes`,
      `chat`, `documents`); в той же миграции — строка «всё включено» для каждой существующей
      организации.
- [ ] 1.2 Агрегат в `domain/` по образцу `attendancecontrol/`: `current()` (нет строки → простой
      набор), `withNew(...)`, `save()`.
- [ ] 1.3 `shared`: `ClientCardSectionsResponse`, `UpdateClientCardSectionsRequest`; маршруты
      GET `org/client-card` и POST `org/client-card/update` в `routes/OrgRoutes.kt`.
- [ ] 1.4 Тесты на сценарии `specs/client-card-sections/spec.md` (по умолчанию, сохранение,
      изоляция организаций, миграция существующих).

## 2. Server: неоплаченные занятия клиента

- [ ] 2.1 `UnpaidSessionsView.byClient(clientId)` + SQL в `DbUnpaidSessionsView` (общий фрагмент с
      `byMembership`), скоуп по `ctx.orgId`, неизвестный клиент → `CLIENT_NOT_FOUND`.
- [ ] 2.2 `shared`: `ClientUnpaidSessionsRequest`; маршрут `clients/unpaid-sessions` в
      `routes/ClientsRoutes.kt`, ответ — `UnpaidSessionsResponse`.
- [ ] 2.3 DB-тест в `server/src/test/kotlin/org/athletica/crm/read/memberships/` на сценарии
      `specs/client-unpaid-sessions/spec.md`.
- [ ] 2.4 `./gradlew build`, `./gradlew ktlintFormat`, `cd web && npm run contracts`, закоммитить
      `web/src/api/generated/`.

## 3. Web: данные и чистая логика

- [ ] 3.1 В `card/clientCardQueries.ts`: запросы разделов карточки и неоплаченных занятий клиента.
- [ ] 3.2 `card/clientOverview.ts`: основной абонемент и «+N», посещаемость по последним 10
      отметкам, история с фильтром и счётчиками; unit-тесты.
- [ ] 3.3 `card/clientCardAlerts.ts`: строки «Требует внимания» с действиями (долг → «Погасить
      долг» / «Выдать абонемент» / «Баланс»; нет абонемента; просроченные задачи только при
      включённом разделе; день рождения); убрать `archived`; обновить тесты.

## 4. Web: страница и вкладки

- [ ] 4.1 `router.tsx`: zod-схема `tab` у `/clients/$clientId` (невалидное → `overview`),
      `/clients/$clientId/visits` → `?tab=sessions`, удалить `/payments` и
      `ClientComingSoonPage.tsx`; тест маршрутов.
- [ ] 4.2 Переписать `ClientDetailPage.tsx`: шапка (`ClientCardHeader`) без плиток и чипов, меню
      «⋯», полоса вкладок со счётчиками и индикатором, выключенная вкладка → `overview`.
- [ ] 4.3 `ClientOverviewTab.tsx`: плитки (переработанный `ClientCardQuickInfo`), «Требует
      внимания», карточки «Группы», «Контакты», «Переписка», «Последняя заметка».
- [ ] 4.4 `ClientSessionsTab.tsx` (из `ClientScheduleSections`), вкладки «Абонементы», «Задачи»,
      «Заметки», «Анкета» на существующих секциях; удалить `card/clientCardBlocks.ts` и `FeedBlock`.
- [ ] 4.5 Панель «Разделы карточки клиента» (`ClientCardSectionsSheet`) из «⋯» и пункт «Карточка
      клиента» в разделе «Клиенты» `SettingsPage.tsx`.
- [ ] 4.6 Раскладка по design.md: ≥ 1000 px, < 1000 px, телефон (плитки, липкие вкладки с выбранной
      в зоне видимости, нижняя панель действий, «⋯» в углу шапки); i18n в `ru.ts` / `en.ts` с
      формами множественного числа.
- [ ] 4.7 Обновить `ClientDetailPage.test.tsx` и тесты секций; тесты на сценарии
      `specs/web-client/**`.

## 5. Проверки

- [ ] 5.1 `npm run format` и `npm run check` в `web/`; `./gradlew build` и `./gradlew ktlintFormat`
      в корне.
