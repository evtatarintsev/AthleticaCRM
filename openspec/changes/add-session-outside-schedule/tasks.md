## 1. Контракт

- [x] 1.1 Добавить обязательное `employeeIds: List<EmployeeId>` в `CreateSessionRequest` (KDoc на русском); проверить `./gradlew :shared:compileKotlinJvm`

## 2. Сервер

- [x] 2.1 `createSession` принимает `employeeIds` и передаёт в `Sessions.new` `employees.byIds(employeeIds)` вместо тренеров группы; маршрут `/sessions/create` пробрасывает поле; проверить `./gradlew compileKotlin`
- [x] 2.2 В `DbSessions.new` после получения `branch_id` группы проверять зал по `org_id` и `branch_id`, иначе `HALL_NOT_FOUND` (сообщение через `Messages`); проверить DB-тестом из 2.4
- [x] 2.3 В `DbSession.reschedule` проверять, что `newHallId` принадлежит организации и филиалу группы занятия, иначе `HALL_NOT_FOUND`, без изменения занятия; проверить DB-тестом из 2.4
- [x] 2.4 DB-тесты в `server/src/test/kotlin/org/athletica/crm/usecases/sessions/CreateSessionTest.kt`: состав отличается от группы → сохранён переданный, `isEmployeeAssignmentOverridden = false`; пустой состав → без тренеров; тренер другого филиала → ошибка; зал другого филиала / другой организации → ошибка, занятие не создано; перенос в зал другого филиала → ошибка, поля прежние; зал своего филиала → успех. `./gradlew test --tests "*CreateSessionTest*"` зелёный
- [x] 2.6 В `DbSession.reschedule` привести `start_time`/`end_time` к `::time` — перенос падал на любом вызове (найдено тестом из 2.4)
- [x] 2.5 `./gradlew build` и `./gradlew ktlintFormat` проходят

## 3. Контракты веба

- [x] 3.1 `cd web && npm run contracts`; в `web/src/api/generated/contracts.ts` у `CreateSessionRequestSchema` появилось `employeeIds`

## 4. Веб: сетка

- [x] 4.1 `ScheduleWeekGrid`: в шапке каждого дня `ghost`-кнопка «[+]» с `aria-label` «Добавить занятие, <день дата>» и колбэком `onAddSession(date)`; тест в `SchedulePage.test.tsx` — клик открывает панель с датой дня
- [x] 4.2 Пустая неделя (нет занятий, ничего не подходит под фильтр, группа другого филиала): `SchedulePage` рендерит сетку, `ScheduleWeekGrid` — одну строку «Занятий нет» на всю ширину; заменить существующий тест «Неделя без занятий» на проверку шапки и кнопок «[+]»

## 5. Веб: панель создания

- [x] 5.1 Компонент `CreateSessionSheet` на `EditSheet`: дата (предзаполнена), группа (`/groups/list` текущего филиала), тренеры (`MultiSelectPicker`, при выборе/смене группы — её `employees`), зал (`/halls/list`), начало/конец, заметка; `id` генерируется при открытии; кнопка «Создать» недоступна при неполных полях, `end <= start` и во время запроса; тест — смена группы заменяет отмеченных тренеров, `end <= start` блокирует создание
- [x] 5.2 Мутация `/sessions/create` в `scheduleQueries.ts`: успех — закрыть панель и инвалидировать запрос расписания; ошибка — оставить панель с данными и показать сообщение; тест на оба исхода
- [x] 5.3 Строки в `i18n/ru.ts` и `en.ts` (заголовок панели, подписи полей, «Занятий нет», `aria-label` кнопки); тексты ошибок API не нужны — их локализует сервер
- [x] 5.4 `npm run format` и `npm run check` в `web/` проходят

## 6. Проверка

- [ ] 6.1 В браузере: пустая неделя показывает сетку с «[+]»; создание занятия из шапки дня добавляет его в сетку; созданное занятие в карточке группы помечено «вне расписания» и не помечено как изменённый состав тренеров
