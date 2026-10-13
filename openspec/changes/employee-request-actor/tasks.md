## 1. Проекции сотрудников

- [ ] 1.1 Создать `read/employees/EmployeeListView.kt` + `DbEmployeeListView.kt` (выход `EmployeeListResponse` с названиями ролей, скоуп по `ctx.orgId`) и DB-тест в `server/src/test/kotlin/org/athletica/crm/read/employees/`, перенеся проверки списка из `DbEmployeesTest`
- [ ] 1.2 Создать `read/employees/EmployeeDetailView.kt` + `DbEmployeeDetailView.kt` (выход `EmployeeDetailResponse`: роли, выдачи, отзывы, филиалы; `EMPLOYEE_NOT_FOUND` для чужой организации) и DB-тест
- [ ] 1.3 Зарегистрировать проекции в `ReadViews`; `employeesRoutes` принимает `ReadViews`, `/list` и `/detail` читают проекции, `/create` собирает `EmployeeListItem` из созданного сотрудника и выбранных ролей; удалить `toListItem`/`toDetailResponse` и `Employees.list()` (с реализациями и стабом); проверить `./gradlew test` и что `cd web && npm run contracts` не меняет `web/src/api/generated/`

## 2. Сотрудник как актор

- [ ] 2.1 `interface Employee : UserActor`; удалить `permissions` из интерфейса; в `DbEmployee` — приватное `permissionSettings` и `hasPermission` через него, `save()`/`withNew` работают с ним; проверить `./gradlew compileKotlin`
- [ ] 2.2 Обновить `EmployeeStub`, анонимный `Employee` в `TaskOperationsTest` и проверки `DbEmployeesTest` (роли — через `hasPermission` или проекции); проверить `./gradlew test --tests "*Employee*"`, включая тест, что сотрудник с ролью проходит её право, а с персональным отзывом — нет

## 3. Контекст запроса

- [ ] 3.1 Добавить в `server/src/test` общий хелпер контекста сотрудника на `EmployeeStub` (orgId, branchId, employeeId, настройка прав, доступ к филиалам) и KDoc к нему; проверить `./gradlew compileTestKotlin`
- [ ] 3.2 Перевести тесты, создающие `EmployeeRequestContext(...)` вручную, на хелпер; проверить, что `grep -rn "EmployeeRequestContext(" server/src/test` находит только хелпер, и `./gradlew compileTestKotlin` проходит
- [ ] 3.3 `EmployeeRequestContext` хранит `employee: Employee` и делегирует `UserActor by employee`; удалить `employeeId`, `availableBranches`, `permission` и импорт `EmployeePermission` из `core`; обновить KDoc; хелпер из 3.1 — под новый состав
- [ ] 3.4 Заменить `ctx.employeeId` → `ctx.employee.id`, `ctx.availableBranches` → `ctx.employee.availableBranches`; в `SetGroupSchedule` брать `ctx.employee.name` без `employees.byId`; `contextFromRequest` и `buildBootstrapContext` передают `employee`; проверить `./gradlew build`

## 4. Документация и проверка

- [ ] 4.1 Обновить пример тестового контекста в `CLAUDE.md` (и упоминания состава контекста в `docs/`, если есть); проверить `grep -rn "permission = EmployeePermission" CLAUDE.md docs` — пусто
- [ ] 4.2 Выполнить `./gradlew build` и `./gradlew ktlintFormat`; убедиться, что `cd web && npm run contracts` не меняет `web/src/api/generated/`
- [ ] 4.3 Под сотрудником в веб-клиенте открыть список и карточку сотрудника, сохранить права и переключить филиал — поведение как до изменения
