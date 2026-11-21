# Tasks

## 1. Схема БД

- [x] 1.1 Liquibase-изменение: `blocked_at`, `last_seen_at`, перенос `NOT is_active AND user_id IS NOT NULL` в `blocked_at`, индекс `one_owner_per_org` без `is_active`, удаление `is_active` с rollback-блоком; проверить, что `./gradlew test` поднимает схему на TestContainers
- [x] 1.2 Обновить комментарии в миграциях-описаниях и `docs/`, где мягкое удаление сотрудника описано через `is_active`, на `blocked_at`; проверить `grep -rn is_active docs server/src/main/kotlin` — пусто

## 2. Домен и вход

- [x] 2.1 `Employee`/`DbEmployee`/`DbEmployees`: `blockedAt` вместо `isActive`, `invite()` без флага и с ошибкой при уже выданном доступе; заменить `is_active` на `user_id IS NOT NULL AND blocked_at IS NULL` в `activeIdsWithAccessTo`, `DueMemberships`, `UserProfile`, `OrgSearch`; проверить существующими тестами этих мест
- [x] 2.2 Команды `block()`/`unblock()` с отказом для владельца и себя, идемпотентной повторной блокировкой и записью в `AuditEmployee`; DB-тесты в `server/src/test/.../domain/` на все сценарии блокировки и разблокировки из `specs/employee-access`
- [x] 2.3 `DbAuth`: вход и `userById` (refresh) отклоняют заблокированного; тесты «вход заблокированного» и «обновление токена» из спеки
- [x] 2.4 `EmployeeBlocked` (`AppError`) в `contextFromRequest` и ответ 401 в `StatusPages`; тест маршрута: заблокированный с действующим токеном получает 401

## 3. Активность

- [x] 3.1 `EmployeePresence` (throttle 1 минута, `UPDATE last_seen_at`) с юнит-тестом: два `touch` в пределах минуты — одна запись, через минуту — вторая
- [x] 3.2 Подключить `EmployeePresence` в `Di.kt` и `contextFromRequest` (транзакция контекста); тест: после запроса `last_seen_at` заполнен

## 4. API и read-проекции

- [x] 4.1 `shared`: `EmployeeAccessStatus`, поля `status`, `lastSeenAt`, `online` вместо `isActive` в `EmployeeListItem`/`EmployeeDetailResponse`; `BlockEmployeeRequest`/`UnblockEmployeeRequest`; `./gradlew build` проходит
- [x] 4.2 `DbEmployeeListView`/`DbEmployeeDetailView`: статус и `online` в SQL, `now` через запрос из routes; DB-тесты в `server/src/test/.../read/employees/` на четыре статуса и порог 5 минут (2 и 10 минут)
- [x] 4.3 Маршруты `/employees/block`, `/employees/unblock`, `now` в запросы проекций; `toListItem()` в `EmployeesRoutes`; `cd web && npm run contracts`, закоммитить `web/src/api/generated/`

## 5. Веб-клиент

- [x] 5.1 `EmployeeSheet`/`employeeForm`: признак подтверждённого выбора филиалов, отправка `allBranchesAccess` по правилу из design, панель открывается со всеми отмеченными при флаге; тесты на сценарии «Ограничение доступа сотрудника со всеми филиалами», «Смена филиала», «Редактирование с доступом ко всем»
- [x] 5.2 Карточка: раздел «Доступ к филиалам» (все / список / пусто) с тестами из спеки
- [x] 5.3 `StatusBadge` на четыре статуса, отметка «В сети», «Был(а) в сети …» (`Intl.RelativeTimeFormat`), колонка активности в списке, фильтр «Только активные» по статусу; строки в `ru.ts`/`en.ts`; тесты списка и карточки
- [x] 5.4 Кнопки «Заблокировать» (с подтверждением) / «Разблокировать» в карточке, скрытые для владельца и себя; «Отправить доступ» только при «Без доступа»; инвалидация списка и карточки; тесты сценариев блокировки
- [x] 5.5 `CreateSessionSheet`: фильтр тренеров `status !== "BLOCKED"`; обновить фикстуры `isActive` в тестах (`schedule`, `tasks`, `groups`, `sessions`, `employees`); `npm run format && npm run check` проходит

## 6. Проверка

- [x] 6.1 `./gradlew build`, `./gradlew ktlintFormat`, `openspec validate employee-access-and-presence --strict` проходят
- [x] 6.2 Ручной прогон в браузере: сменить филиал сотруднику со «всеми», заблокировать — вкладка сотрудника получает выход на следующем запросе, разблокировать, увидеть «В сети»

## Workflow follow-up

- Отдельное изменение: право «управление сотрудниками» (сейчас редактировать и блокировать может любой сотрудник).
- Архивировать изменение после ревью.
