## Context

Мотивация — в proposal.md, требования — в `specs/web-client/employees/spec.md` и `specs/web-client/app-shell/spec.md`. На что опирается решение:

- `web/src/employees/EmployeeCreatePage.tsx` и `EmployeeEditPage.tsx` — TanStack Form только для имени, телефона и email; аватар, роли, права и филиалы — отдельное состояние `EmployeeExtras` (`useState`), которое рендерит `EmployeeFormFields.tsx`. Взаимоисключение выданных и отозванных прав — ручная логика в `setGranted`/`setRevoked`.
- `employees/create` и `employees/update` принимают всё одним запросом: `roleIds`, `grantedPermissions`, `revokedPermissions`, `allBranchesAccess`, `branchIds`, `avatarId`.
- `RoleItem` из `employees/roles` содержит `permissions` — по нему можно вычислить права из ролей на клиенте.
- `UserPermission` — статичный enum (сейчас 4 значения), название и описание — `permission.<X>.name` / `.description` в словарях.
- `web/src/ui/EditSheet.tsx`: `EditSheet` (защита изменений через `reportGuard`, монтирование содержимого при каждом открытии, вложенная панель сдвигает родительскую), `EditSheetForm` (`nested` — вложенные панели вне `<form>`).
- `web/src/ui/ChecklistSheet.tsx` — множественный выбор с «Сохранить», `onSubmit(ids) => Promise<string | null>`, без поиска.
- Прецедент панелей в адресе — задачи (`TaskListSearchSchema`, `task`/`create`, `navigate({ replace: true })`); прецедент записи выбора вложенной панели в форму — `TaskEditSheet` с `ClientPickerSheet` (`onSubmit` вызывает `form.setFieldValue` и возвращает `null`).
- `isDirty` TanStack Form как источник `dirty` — `ProfileSheet`, `TaskCreateSheet`, `TaskEditSheet`.

## Goals / Non-Goals

**Goals:**
- Одна панель-форма для создания и редактирования; поля формы и сборка запроса не дублируются.
- Конфликт «выдано и отозвано» непредставим в значениях формы, а не предотвращается обработчиками.

**Non-Goals:**
- Новый общий компонент выбора с тремя состояниями в `ui/` — панель прав остаётся в `employees/`, пока второго потребителя нет (редактор ролей выбирает права двумя состояниями).
- Виртуализация длинных списков: даже сотни прав — дешёвый DOM.

## Decisions

### 1. Структура панелей

```
EmployeesPage (search: employee?: EmployeeId, edit?: true, create?: true)
 |-- EmployeeSheet mode=create        EditSheet md, EditSheetForm "Создать"
 '-- EmployeeCardSheet                EditSheet md, EditSheetBody, без формы
       фото, статус, контакты, роли; [Отправить доступ] [Редактировать]
       '-- EmployeeSheet mode=edit    EditSheet md, EditSheetForm "Сохранить"

EmployeeSheet
  +-- фото (AvatarPicker), имя, телефон, email
  +-- Роли      [Изменить] --> ChecklistSheet + поиск   (nested)
  +-- Права     [Изменить] --> PermissionsSheet            (nested)
  '-- Филиалы   [Изменить] --> ChecklistSheet + поиск   (nested)
```

Одна `EmployeeSheet` с режимом вместо двух компонентов: отличаются только начальные значения, запрос (`employees/create` с новым `uuidv7()` или `employees/update` с `id`), подпись кнопки, флаг `allBranchesAccess` и что инвалидировать после успеха. Роли и филиалы загружаются внутри панели (`employees/roles`, `branches/list`) со скелетоном в `EditSheetBody`, пока не пришли; для редактирования сотрудник передаётся пропсом — карточка его уже загрузила, как `TaskEditSheet` получает `task`.

`EmployeeCardSheet` — бывшая `EmployeeDetailPage`, перенесённая в панель как `TaskSheet`: монтируется постоянно с `open={employee !== undefined}` и грузит `employees/detail` только при открытой панели. Без формы `dirty` всегда `false`, крестик и Esc закрывают карточку сразу; вложенная панель редактирования защищает свои изменения сама.

После создания панель закрывается, инвалидируется `employees/list`, карточка не открывается (как у задач — пользователь остаётся в списке). После редактирования — инвалидация `employees/detail` и `employees/list`, закрытие панели редактирования; карточка под ней обновляется.

*Альтернатива:* сохранять каждую вложенную панель сразу на сервер, как тренеры группы. Отклонено: при создании сотрудника ещё нет, а для редактирования это превратило бы форму в смесь «сохраняется сразу» и «по кнопке».

### 2. Адрес: `employee`, `edit`, `create`

`/employees` получает `validateSearch` со схемой `{ employee: EmployeeIdSchema, edit: true, create: true }` (все `optional().catch(undefined)`). Строки списка — `<Link to="/employees" search={{ employee }} replace>`, «Добавить сотрудника» — `search={{ create: true }}`; «Редактировать» в карточке добавляет `edit: true` к `employee`; закрытие панелей — `navigate({ search, replace: true })`. `employee` важнее `create`; `edit` без `employee` ничего не открывает. Поиск и фильтр «только активные» списка остаются локальным состоянием и в адрес не переносятся.

Маршруты `/employees/new`, `/employees/$employeeId`, `/employees/$employeeId/edit` и запись `"/employees/new"` в `staticAppPaths` удаляются без редиректов; требование app-shell обновляется (delta-спека).

*Альтернатива:* оставить карточку страницей, а редактирование — панелью поверх неё. Отклонено после первой итерации: просмотр уводил из списка, в отличие от задач.

### 3. Значения формы

Все поля переходят в TanStack Form, `EmployeeExtras` и `EmployeeFormFields` удаляются:

```ts
type PermissionOverride = "grant" | "revoke";

interface EmployeeFormValues {
  name: string;
  phoneNo: string;
  email: string;
  avatarId: UploadId | null;
  roleIds: readonly string[];
  permissions: Readonly<Partial<Record<UserPermission, PermissionOverride>>>;
  branchIds: readonly BranchId[];
}
```

`permissions` — одна запись на право: отсутствие ключа — «По роли», значение — явное решение. Так одновременно выданное и отозванное право непредставимо, а логика взаимоисключения исчезает. В `grantedPermissions`/`revokedPermissions` запроса значения раскладываются при отправке; из `EmployeeDetailResponse` — собираются при открытии (если сервер когда-либо вернёт право в обоих списках, побеждает отзыв — безопаснее). Обязательность имени и email проверяют валидаторы полей (`schema.shape.name` / `.email` из `employeeContactSchema`): схема всей формы не подходит, потому что остальные поля в ней не описаны. `dirty` — `state.isDirty`.

*Альтернатива:* оставить два `Set` (`granted`, `revoked`). Отклонено: инвариант пришлось бы держать обработчиками, а с одной панелью на три состояния словарь естественнее.

### 4. `ChecklistSheet`: необязательный поиск и подпись кнопки

Новые пропсы `searchLabel?: string` (подпись и placeholder поля поиска; без неё поля нет) и `submitLabel?: string`. При `searchLabel` над списком — `Input type="search"`, список фильтруется по `name` (`toLocaleLowerCase().includes`), при пустом результате — «Ничего не найдено» (`picker.nothingFound`); Enter в поле поиска форму не отправляет. Черновик отметок не зависит от фильтра: скрытые поиском отмеченные записи остаются в черновике и уходят в `onSubmit`. Поведение без новых пропсов не меняется — группы и задачи не затрагиваются.

В форме сотрудника `onSubmit` вызывает `form.setFieldValue("roleIds" | "branchIds", ids)` и возвращает `Promise.resolve(null)`; `submitLabel` — «Готово» (`action.done`), а не «Сохранить», чтобы не обещать сохранение на сервер.

### 5. `PermissionsSheet`

```
PermissionsSheet({ open, onOpenChange, value, rolePermissions, onApply })
  value: Partial<Record<UserPermission, PermissionOverride>>
  rolePermissions: ReadonlySet<UserPermission>   // объединение RoleItem.permissions выбранных ролей

  EditSheet md, EditSheetForm "Готово"
  [поиск по t(name) + t(description)]
  для каждого UserPermissionSchema.options, прошедшего поиск:
    название                          ( По роли | Выдано | Отозвано )
    описание          [из роли]
```

Переключатель — группа из трёх нативных `<input type="radio">` с общим `name` на право, оформленная как сегменты: клавиатура и скринридеры работают без своей логики. Черновик — копия `value`; `dirty` — сравнение черновика с `value`; «Готово» отдаёт черновик в `onApply`, родитель пишет его в `form.setFieldValue("permissions", ...)`. `rolePermissions` считается в `EmployeeSheet` из текущих `roleIds` формы, поэтому пометка «из роли» сразу отражает роли, выбранные в этой же панели.

Сводка секции в форме: «Выдано: N, отозвано: M» или «Права определяются ролями».

### 6. Флаг `allBranchesAccess`

Не входит в значения формы. `employees/create` отправляет `false`; `employees/update` — `employee.allBranchesAccess` из пропса. Если флаг включён, секция филиалов показывает пометку «Доступ ко всем филиалам» (существующий ключ `employees.allBranches`) над списком выбранных — пользователь видит, почему список не ограничивает доступ. Когда флаг уберут на сервере, удаляются две строки сборки запроса и пометка.

## Risks / Trade-offs

- [`isDirty` в TanStack Form не сбрасывается, если пользователь вернул исходное значение] → так же ведут себя `ProfileSheet` и задачи; лишнее подтверждение безопаснее потерянных изменений.
- [Сотрудник с включённым флагом «все филиалы» не может получить ограниченный доступ из веба до удаления флага на сервере] → осознанно принято; пометка в секции объясняет состояние.
- [Старые закладки `/employees/new`, `/employees/{id}` и `/employees/{id}/edit` ведут на «не найдено»] → адреса внутренние, сервер ссылок на них не формирует; зафиксировано в app-shell.
- [Пометка «из роли» считается на клиенте и может разойтись с серверной логикой эффективных прав] → правило простое (объединение прав ролей), сервер остаётся источником истины для проверок; пометка — подсказка, не гарантия.

## Migration Plan

Только веб-клиент, без серверных изменений и контрактов: выкатывается обычным деплоем, откат — откат коммита.
