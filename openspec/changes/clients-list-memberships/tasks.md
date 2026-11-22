# Tasks

## 1. Схемы `shared`

- [x] 1.1 Добавить `ClientListMembership` и sealed `ClientListMembershipState` (`inactive`: `activationMode`, `activateOn`; `active`: `startDate`, `endDate`, `termPosition`, `endingSoon`, `closePending`) и поля `memberships`, `unpaidSessions` в `ClientListItem` (design §1); проверка — `./gradlew :shared:compileKotlinJvm`
- [x] 1.2 Добавить `enum class ClientListColumn` (`groups`, `memberships`, `balance`) и сменить умолчание `ClientsDisplaySettings.columns` на эти ключи; `ClientField` не трогать (design §4); проверка — компиляция и обновлённый KDoc `ClientsDisplaySettings`

## 2. Проекция `DbClientListView`

- [x] 2.1 Добавить обязательное `now: Instant` в `ClientListQuery`; `toQuery(now)` и оба маршрута (`clients/list`, `clients/export`) передают `Clock.System.now()`; поправить вызовы в `DbClientListViewTest` и `ClientListRoutesTest` — проверка: `./gradlew test --tests "*ClientList*"` зелёный
- [x] 2.2 Добавить в запрос страницы `today` (через `organizations.timezone`), `jsonb_agg` абонементов `ACTIVE`/`INACTIVE` с SQL-порядком `end_date NULLS LAST, issued_on, created_at, id` и маппинг в схему с `ActiveStatus.of` и стабильной сортировкой по частям (design §2); DB-тесты в `server/src/test/kotlin/org/athletica/crm/read/clients/` на сценарии `client-list` «Два абонемента», «Абонемент без группы», «Закрытый и отозванный не отдаются», «Действующий раньше будущего», «Заканчивается по занятиям», «Срок вышел», «Срок ещё не начался»
- [x] 2.3 Добавить `unpaid_sessions` по группам филиала `ctx.branchId` (design §2); DB-тесты «Неоплаченные в разных группах», «Другой филиал», «Сторнированное списание», плюс «не пришёл» и непроведённое занятие не считаются, клиент без долга — 0
- [x] 2.4 Тест «Пагинация не меняется»: 120 клиентов страницами по 50 — `total` = 120, у клиентов страницы есть абонементы и `unpaidSessions`; проверка — `./gradlew test --tests "*DbClientListViewTest*"`
- [x] 2.5 Обновить KDoc `ClientListView`/`DbClientListView` (что собирается и откуда «сегодня») и проверить, что экспорт CSV не изменился существующими тестами экспорта

## 3. Сброс сохранённых колонок

- [x] 3.1 Changeset `0090-reset-client-list-columns.sql` (следующий свободный номер): `settings #- '{clients,columns}'` (design §5); тест: у пользователя с сохранёнными колонками `phone, birthday, groups` после миграции `DbUserDisplaySettings.get()` отдаёт `groups, memberships, balance`, остальные настройки (`savedViews`, другие таблицы) на месте

## 4. Контракты веба

- [x] 4.1 `cd web && npm run contracts`, закоммитить `web/src/api/generated/`; проверка — `npm run typecheck` показывает только ожидаемые ошибки в местах, которые правятся в группе 5

## 5. Веб: колонки, строка, метки

- [x] 5.1 Разделить каталоги в `clientColumns.ts`: `ClientColumnKey = groups | memberships | balance`, `CLIENT_COLUMN_KEYS`; список полей экспорта (`birthday`, `gender`, `groups`, `balance`) и подписи — в `ClientExportDialog`; `sortableField` → только `balance`; тесты: `resolveClientColumns` отбрасывает `gender`/`birthday`, `availableClientColumns` не предлагает их, экспорт по-прежнему предлагает «Пол» и «Дату рождения»
- [x] 5.2 Убрать `birthday` из `ClientSortColumn` и zod-enum `sort` в `clientListSearch.ts` (порядок `nextBirthday` для фильтра дня рождения остаётся); тест в `clientListSearch.test.ts`: `?sort=birthday` из URL даёт сортировку по имени, `nextBirthday` с фильтром работает
- [x] 5.3 `clientListMembershipLabel()` в `web/src/clients/clientListMembership.ts` по правилам «Метки абонемента» (design §6) и ключи словарей `ru`/`en`; vitest на все сценарии требования «Метки абонемента» и «Колонка „Абонемент“» (безлимит, срок вышел + закончились занятия, заканчивается по обоим признакам, активный с будущей датой, «С первого посещения», «Нет абонемента»)
- [x] 5.4 `ClientListTable` на компьютере: пол и возраст под именем (`ageOn`, `clientCard.meta.age`, без даты — только пол), все группы чипами друг под другом без «+N», колонка «Абонемент», «N неоплач.» красным под балансом; тесты рендера на сценарии «Пол и возраст под именем», «Три группы», «Два абонемента», «Неоплаченные под балансом», «Заголовки таблицы» (нет сортировки по дате рождения)
- [x] 5.5 `ClientListTable` на телефоне: баланс с неоплаченными справа, подложка с группами и метками по настройкам колонок, без подложки при выключенных «Группы» и «Абонемент», без карандаша; тесты «Строка на телефоне», «Абонементы выключены»
- [x] 5.6 `ClientColumnsDialog` предлагает «Группы», «Абонемент», «Баланс», контакты и доп. поля; тесты «Новый сотрудник» (умолчание из ответа настроек) и «Выключенный абонемент»
- [x] 5.7 Удалить ставшие ненужными ключи словарей (`clients.groupsMore`, `clients.column.gender/birthday`, если больше не используются в таблице) — проверка: `npm run check` без неиспользуемых ключей и ошибок

## 6. Проверка целиком

- [x] 6.1 `./gradlew build` и `./gradlew ktlintFormat` без изменений после форматирования
- [x] 6.2 В `web/`: `npm run format` и `npm run check` зелёные
- [x] 6.3 Запустить сервер с вебом, открыть список клиентов на 1280 px и 390 px и сверить с `mockups/clients-list-memberships.html`: колонки по умолчанию, метки абонементов, «N неоплач.», выключение «Абонемента» в «Колонках»

## Workflow follow-up

- После ревью и мержа — синхронизировать дельты в `openspec/specs` и архивировать change (`openspec-archive-change`).
