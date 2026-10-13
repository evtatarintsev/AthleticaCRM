## Why

Актор аутентифицированного запроса — сотрудник, но `EmployeeRequestContext` хранит его по частям:
`employeeId`, `availableBranches` и сырую настройку прав `EmployeePermission`, которые
`contextFromRequest` и `buildBootstrapContext` вытаскивают из загруженного `Employee`.
Из-за этого правила, принадлежащие сотруднику (например, «владелец имеет все права» из
`owner-all-permissions`), некуда встроить, кроме контекста, а интерфейс `Employee` раскрывает
устройство прав (`permissions: EmployeePermission`) вместо ответа на вопрос «что может сотрудник».

## What Changes

- `Employee` становится актором: `interface Employee : UserActor`. Проверка прав — `employee.hasPermission(p)`.
- Из интерфейса `Employee` убирается свойство `permissions: EmployeePermission`. Настройка прав
  (роли, персональные выдачи и отзывы) остаётся входом команд `Employees.new` и `Employee.withNew`
  и приватным состоянием реализации, которое пишет `save()`.
- `EmployeeRequestContext` хранит `employee: Employee` и делегирует `UserActor by employee`;
  поля `employeeId`, `availableBranches` и `permission` удаляются (`ctx.employee.id`,
  `ctx.employee.availableBranches`). `userId` и `username` остаются полями сессии из JWT.
- Список и карточка сотрудника (`GET /employees/list`, `GET /employees/detail`) читаются
  проекциями `read/employees/` вместо сборки ответа из агрегата; `Employees.list()` удаляется.
- Тесты собирают контекст сотрудника общим хелпером вместо ручного `EmployeeRequestContext(...)`.
- Поведение API, схемы запросов и ответов и сгенерированные контракты веб-клиента не меняются.

## Capabilities

### New Capabilities
<!-- нет: чистый рефакторинг, наблюдаемое поведение не меняется (skip_specs) -->

### Modified Capabilities
<!-- нет -->

## Impact

- `server/.../core/RequestContext.kt` — новый состав `EmployeeRequestContext`.
- `server/.../domain/employees/` — `Employee : UserActor`, `DbEmployee`, `DbEmployees` (без `list()`),
  декораторы `AuditEmployee` / `EmailEmployee` (делегирование `hasPermission` через `by delegate`).
- `server/.../read/employees/` — новые `EmployeeListView`, `EmployeeDetailView`; регистрация в `ReadViews`.
- `server/.../routes/` — `RouteWithContext.kt`, `AuthRoutes.kt` (сборка контекста), `EmployeesRoutes.kt`
  (проекции вместо `toListItem`/`toDetailResponse`), `Application.kt` (передача `ReadViews`).
- Все обращения к `ctx.employeeId` (~23) и `ctx.availableBranches` (2) в `server/src/main`.
- ~36 тестовых файлов, создающих `EmployeeRequestContext`; `EmployeesStub`, `DbEmployeesTest`.
- `CLAUDE.md` — пример тестового контекста.
- Разблокирует `owner-all-permissions`: правило владельца становится строкой в `DbEmployee.hasPermission`.
