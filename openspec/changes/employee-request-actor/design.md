## Context

Мотивация — в `proposal.md`. Спецификации не меняются: рефакторинг не меняет наблюдаемого поведения.

Сейчас:
- `EmployeeRequestContext(lang, orgId, currency, userId, branchId, employeeId, username, clientIp,
  availableBranches, private permission: EmployeePermission) : RequestContext, UserActor by permission`.
- Контекст собирают только `contextFromRequest` (`RouteWithContext.kt`) и `buildBootstrapContext`
  (`AuthRoutes.kt`); оба уже загружают `employees.byId(employeeId)` и копируют из него
  `permissions` и `availableBranches`.
- `Employee.permissions: EmployeePermission` читают: `EmployeesRoutes.toListItem()` (названия ролей),
  `toDetailResponse()` (роли, выдачи, отзывы), `DbEmployee.save()`, тесты. Больше нигде.
- `Employee.userId: UserId?` допускает null (сотрудник без учётной записи); `ctx.userId` — ненулевой
  идентификатор пользователя из JWT.
- `AuditEmployee` и `EmailEmployee` — декораторы `Employee by delegate`.
- `SetGroupSchedule` дозагружает `employees.byId(ctx.employeeId).name` ради имени автора изменения.
- ~36 тестовых файлов вручную создают `EmployeeRequestContext(..., permission = EmployeePermission(...))`.

## Goals / Non-Goals

**Goals:**
- Один источник ответа «что может сотрудник» — сам `Employee`; контекст лишь делегирует ему.
- Интерфейс `Employee` не раскрывает устройство прав.
- Чтение списка и карточки сотрудника — через `read/`, по правилам слоя проекций.

**Non-Goals:**
- Правило владельца — `owner-all-permissions` (реализуется следующим).
- Узкий интерфейс актора вместо `Employee` в контексте.
- Изменения API-схем, веб-клиента, БД.
- Кеширование загрузки сотрудника между запросами.

## Decisions

### 1. `Employee : UserActor`, настройка прав — приватное состояние реализации

`interface Employee : UserActor`. Свойство `permissions` удаляется из интерфейса. `DbEmployee`
хранит настройку в приватном поле `permissionSettings: EmployeePermission` и реализует
`hasPermission(p) = permissionSettings.hasPermission(p)`; `save()` пишет роли и выдачи из него же.
Изменение прав — по-прежнему через `withNew(..., newPermissions, ...)`: настройка — вход команды,
а не читаемое свойство. Декораторы получают `hasPermission` через `by delegate` без правок.

Альтернативы:
- `permissions: UserActor` + `permissionSettings: EmployeePermission` в интерфейсе — два свойства
  с правами, и геттер настройки нужен только отображению, которое уходит в `read/`.
- `Employees.setPermissions(EmployeeId, EmployeePermission)` — обходит агрегат: будущие инварианты
  (например, «права владельца не редактируются») естественно живут в `Employee`, а не в репозитории по id.

### 2. Контекст хранит `Employee` целиком

`EmployeeRequestContext(lang, orgId, currency, userId, branchId, username, clientIp, employee: Employee)
: RequestContext, UserActor by employee`. `employeeId` → `ctx.employee.id`, `availableBranches` →
`ctx.employee.availableBranches`; `SetGroupSchedule` берёт `ctx.employee.name` без дозагрузки.
`userId` и `username` остаются: это сессия из JWT, а `Employee.userId` nullable.

Альтернативы:
- Узкий интерфейс (`id`, `name`, `availableBranches`, `UserActor`) — строже (через контекст не видны
  команды `save`/`withNew`), но добавляет тип без текущей потребности; решено хранить `Employee`.
- Оставить поля и передавать `employee` как `UserActor` — дублирует `employeeId`/`availableBranches`
  с источником истины.

`core` уже импортирует `domain.employees` (`EmployeeBranchAccess`, `EmployeePermission`), поэтому
ссылка `EmployeeRequestContext → Employee` не создаёт новой зависимости между пакетами; импорт
`EmployeePermission` из `core` уходит.

### 3. Список и карточка сотрудника — проекции `read/employees/`

`EmployeeListView` → `EmployeeListResponse` (с названиями ролей) и `EmployeeDetailView` →
`EmployeeDetailResponse` (роли, выдачи, отзывы, филиалы), по шаблону `read/clients/ClientListView`:
один SQL на запрос, скоуп по `ctx.orgId`, выход — схемы `api.schemas` напрямую; регистрация в `ReadViews`,
`employeesRoutes` принимает `ReadViews`. `Employees.list()` и `toListItem`/`toDetailResponse` удаляются.
Ответ `/employees/create` (`EmployeeListItem`) собирается в маршруте из созданного `Employee` и
выбранных в запросе ролей — оба уже есть в обработчике.

### 4. Общий тестовый хелпер контекста

В `server/src/test` — функция, создающая `EmployeeRequestContext` с `EmployeeStub` (параметры:
`orgId`, `branchId`, `employeeId`, настройка прав, доступ к филиалам; остальное — значения
по умолчанию). Тесты переводятся на неё, чтобы следующая смена состава контекста правила одно место.
`EmployeeStub` реализует `hasPermission` через свою настройку прав.

## Risks / Trade-offs

- [Через `ctx.employee` доступны команды агрегата (`withNew`, `save`)] → осознанно; запись по-прежнему
  требует `Transaction` и явного вызова; при злоупотреблении — перейти к узкому интерфейсу (см. Решение 2).
- [Снимок сотрудника в контексте устаревает внутри запроса, изменившего самого сотрудника] →
  так же, как сейчас с правами (point-in-time, см. CLAUDE.md «Known Constraints»).
- [Большой механический дифф в тестах] → сначала хелпер, затем перевод тестов; компиляция
  `compileTestKotlin` — критерий готовности шага.
- [Проекции дублируют SQL загрузки ролей из `DbEmployees`] → допустимо по правилам `read/`:
  проекции не импортируют `domain/**`.

## Migration Plan

Внутренний рефакторинг сервера без миграций БД и изменений API; выкатывается обычным деплоем,
откат — откатом коммита.
