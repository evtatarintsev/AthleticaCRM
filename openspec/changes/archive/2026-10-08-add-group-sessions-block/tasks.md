## 1. Контракты

- [x] 1.1 В `shared/.../api/schemas/groups/` добавить `GroupSessionsRequest` (`groupId`, `from`, `to`) и `GroupSessionsResponse` (`sessions`, `last?`, `next?`) со строкой `GroupSessionSchema`: `id`, `date`, `startTime`, `endTime`, `hall {id, name}`, `coaches [{id, name}]`, `coachesOverridden`, `status: SessionStatus`, `rescheduledFrom: LocalDate?`, `isManual`, `attendance: {present, total}?` (переиспользовать `ScheduleHallSchema`/`ScheduleCoachSchema`, если подходят). KDoc на русском у классов и полей. Проверка: `./gradlew :shared:compileKotlinJvm`
- [x] 1.2 В `ScheduleListRequest` добавить необязательное `groupIds: List<GroupId>? = null`. Проверка: `./gradlew :shared:compileKotlinJvm`

## 2. Сервер: проекция занятий группы

- [x] 2.1 Вынести SQL-агрегат тренеров занятия (`COACHES_JSON` из `DbScheduleView`) в общий объект `read/` и перевести `DbScheduleView` на него. Проверка: существующие тесты `read/schedule` проходят
- [x] 2.2 Создать `read/groups/GroupSessionsView.kt` (интерфейс + `GroupSessionsQuery`) и `DbGroupSessionsView.kt`: проверка группы в `ctx.orgId`/`ctx.branchId` (`GROUP_NOT_FOUND`), один SQL на строки периода по `date, start_time`; подзапросы последнего (не `cancelled`, `(date + start_time) AT TIME ZONE o.timezone <= now()`) и ближайшего (`scheduled`, начало в будущем); `rescheduledFrom` — `origin_date`, только если `is_rescheduled` и дата отличается; `isManual` — `origin_slot_id IS NULL`; `attendance` — по журналу, только для `completed`. Зарегистрировать в `ReadViews`. Проверка: `./gradlew compileKotlin`
- [x] 2.3 Проверить наличие индекса по `sessions (group_id, date)`; при отсутствии — Liquibase-changeset. Проверка: `./gradlew test --tests "*Liquibase*"` либо запуск любого DB-теста
- [x] 2.4 DB-тест `server/src/test/kotlin/org/athletica/crm/read/groups/GroupSessionsViewTest.kt`: только занятия группы и периода, все статусы, сортировка; перенесённое — `rescheduledFrom`; ручное — `isManual`; заменённый тренер — `coachesOverridden` и тренеры занятия; проведённое — `attendance`, запланированное и отменённое — `null`; последнее и ближайшее вне периода, отменённое не становится ни тем, ни другим, у новой группы оба `null`; группа другого филиала/организации — ошибка. Проверка: `./gradlew test --tests "*GroupSessionsViewTest"`
- [x] 2.5 Маршрут чтения в `GroupsRoutes` (по образцу `schedule/list`: GET, параметры в query): нормализация и проверка периода (начало ≤ конец, ≤ `MAX_SCHEDULE_PERIOD_DAYS`) в маршруте, вызов `views.groupSessions`. Тест маршрута на некорректный период и успешный ответ. Проверка: `./gradlew test --tests "*Groups*"`

## 3. Сервер: фильтр расписания по группе

- [x] 3.1 `ScheduleQuery.groupIds` и условие `AND s.group_id = ANY(:groupIds)` в `DbScheduleView`; маршрут `schedule/list` передаёт `groupIds`. Тест в `read/schedule`: фильтр по группе, сочетание с фильтром зала, группа другого филиала — пустой ответ. Проверка: `./gradlew test --tests "*Schedule*"`
- [x] 3.2 Перегенерировать контракты `cd web && npm run contracts`, закоммитить `web/src/api/generated/`. Проверка: `git diff --stat web/src/api/generated` показывает новые схемы, `npm run check` в `web/` проходит

## 4. Веб: блок «Занятия»

- [x] 4.1 Модуль окна `web/src/groups/groupSessionsWindow.ts`: окно по умолчанию `[today − 7, today + 14]`, сдвиг на длину окна назад/вперёд, признак «сегодня» для разделителя. Юнит-тест `groupSessionsWindow.test.ts`. Проверка: `npx vitest run src/groups`
- [x] 4.2 Компонент `web/src/groups/GroupSessionsSection.tsx`: запрос через `apiQuery` с ключом по `groupId` и окну; позиции «Последнее»/«Ближайшее» над списком; строки — ссылка на `/sessions/$sessionId` с датой, временем, залом, тренерами (с отметкой замены), статусом (`sessionStatusLabelKey`), бейджами «перенесено с …» и «вне расписания», посещаемостью «N из M»; отменённое визуально приглушено; разделитель «сегодня»; пустое состояние; скелетон загрузки и ошибка с повтором; кнопки сдвига окна с подписью диапазона и «Календарь» (`/schedule?groupIds=…`). Все строки — из `ru.ts`/`en.ts`. Проверка: `npm run check`
- [x] 4.3 Тесты `GroupSessionsSection` (или в `GroupDetailPage.test.tsx`): строки и бейджи по ответу; последнее и ближайшее показаны и скрываются при `null`; сдвиг окна запрашивает новый период; клик по строке открывает карточку занятия; «Календарь» ведёт в расписание с фильтром; ошибка показывает повтор, остальные секции карточки отображаются. Проверка: `npx vitest run src/groups`

## 5. Веб: раскладка карточки группы

- [x] 5.1 `GroupDetailPage.tsx`: контейнер `max-w-6xl`, сетка `xl:grid xl:grid-cols-[minmax(0,1fr)_20rem]`; основная колонка — заголовок, дисциплины, расписание, тренеры, `GroupSessionsSection`; вторая — секция клиентов, `xl:sticky` под шапкой с `max-h` по высоте экрана и собственной прокруткой; в одной колонке порядок «занятия → клиенты». Скелетон и ошибка загрузки карточки не ломаются. Проверка: `npx vitest run src/groups` — существующие тесты карточки проходят

## 6. Веб: фильтр расписания по группе

- [x] 6.1 В search-схему расписания добавить `groupIds` (zod, с `.catch`), прокинуть в `scheduleFiltersOf`/`scheduleListRequest`/`activeScheduleFilterCount`; на `SchedulePage` при активном фильтре — снимаемый чип с названием группы из `groups/list-for-select` (запасной текст «Группа», если её нет в списке); снятие убирает `groupIds` из адреса; фильтр сохраняется при смене недели. Проверка: `npm run check`
- [x] 6.2 Тесты `SchedulePage.test.tsx`: открытие по адресу с `groupIds` отправляет фильтр в запрос и показывает чип с названием; снятие чипа убирает фильтр; переключение недели сохраняет фильтр. Проверка: `npx vitest run src/schedule`

## 7. Завершение

- [x] 7.1 `./gradlew build` и `./gradlew ktlintFormat` проходят; в `web/` — `npm run format` и `npm run check` проходят
- [x] 7.2 Вручную в превью: карточка группы на ширине ≥ 1280px — две колонки, при группе 30+ клиентов занятия остаются под тренерами, список клиентов прокручивается сам; на мобильной ширине — одна колонка, занятия перед клиентами; сдвиг окна, переход в карточку занятия, «Календарь» открывает неделю с чипом группы
