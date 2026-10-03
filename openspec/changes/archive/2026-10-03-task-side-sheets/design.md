## Context

Мотивация — в proposal.md, требования — в `specs/web-client/tasks/spec.md`. На что опирается решение:

- `web/src/tasks/TaskCreatePage.tsx` — TanStack Form для заголовка и описания, остальное (исполнитель, сроки, вложения) — отдельное состояние `TaskExtras`; вложения загружаются до создания и привязываются `tasks/attach` после `tasks/create`. Всегда шлёт `clientId: null`.
- `web/src/tasks/TaskDetailPage.tsx` — не форма: статус, исполнитель, сроки и вложения сохраняются сразу по `onChange`; сроки уходят в `tasks/update` вместе с текущими `title`/`description`/`clientId` из `tasks/detail`.
- `tasks/update` перезаписывает `title`, `description`, `client_id`, `due_date`, `due_date_end` целиком; исполнитель, статус и вложения — отдельные эндпоинты.
- `web/src/ui/EditSheet.tsx`: `EditSheet` (защита изменений через `reportGuard`, содержимое монтируется заново при каждом открытии, вложенная панель сдвигает родительскую влево), `EditSheetForm` (поля + «Отмена»/«Сохранить», `nested` для вложенных панелей вне `<form>`), `EditSheetBody`.
- `web/src/ui/ChecklistSheet.tsx` — множественный выбор с «Сохранить»; контракт `onSubmit(ids) => Promise<string | null>`.
- `web/src/clients/ClientPickerSheet.tsx` — режим `single`: нажатие сразу вызывает `onSubmit([client])`, получает `{ id, name }`.
- Прецедент панели в адресе — `/settings?panel=...` (`SettingsSearchSchema`, `navigate({ replace: true })`).
- Формы-панели на TanStack Form берут `dirty` из `state.isDirty` (`ProfileSheet`, `DirectoryItemSheet`).
- На `/tasks/new` и `/tasks/{id}` ведут только сам раздел задач: сервер ссылок на задачи не формирует, в KMP-адресах из `app-shell` задач нет.

## Goals / Non-Goals

**Goals:**
- Одна структура панелей для создания, карточки и редактирования; общие поля формы не дублируются.
- Компонент одиночного выбора — общий, в `ui/`, без знания о задачах.

**Non-Goals:**
- Переделка `ClientPickerSheet` и `ChecklistSheet`.
- Оптимистичные обновления: после каждого изменения — инвалидация запросов, как сейчас.

## Decisions

### 1. Структура панелей

```
TasksPage  (search: ...фильтры, task?: TaskId, create?: true)
 |-- TaskCreateSheet         EditSheet md, EditSheetForm "Создать"
 |     TaskFormFields + Исполнитель + Вложения
 |     nested: ClientPickerSheet(single) | ChoiceSheet(сотрудники)
 |-- TaskSheet               EditSheet lg, EditSheetBody, без формы
 |     заголовок, статус [меню], исполнитель [Изменить] -> ChoiceSheet
 |     клиент, сроки, описание - чтение; вложения +/x; [Редактировать]
 |     '-- TaskEditSheet     EditSheet md, EditSheetForm
 |           TaskFormFields;  nested: ClientPickerSheet(single)
 '-- ChoiceSheet             массовое «Назначить» из SelectionBar
```

`TaskSheet` монтируется постоянно с `open={search.task !== undefined}` и грузит `tasks/detail` только при открытой панели. Без формы `dirty` всегда `false`, поэтому крестик и Esc закрывают карточку сразу; вложенная `TaskEditSheet` со своей формой защищает собственные изменения.

*Альтернатива:* вся карточка — одна форма с «Сохранить». Отклонено: статус и исполнитель — отдельные эндпоинты, «Сохранить» стало бы цепочкой запросов с частичными отказами, а самое частое действие (смена статуса) требовало бы двух нажатий.

### 2. Адрес: `task` и `create` в `TaskListSearchSchema`

В схему добавляются `task: TaskIdSchema.optional().catch(undefined)` и `create: z.literal(true).optional().catch(undefined)`. Некорректный идентификатор сбрасывается схемой — панель не открывается и запроса нет. Открытие и закрытие панели — `navigate({ search: { ...search, task } , replace: true })`, чтобы «Назад» не листал открытия панелей; фильтры не трогаются. Если в адресе оба параметра, открывается карточка (`task` важнее), `create` игнорируется.

Строки списка остаются `<Link to="/tasks" search={...}>` с текущими фильтрами и `task`: работает открытие в новой вкладке, а `after:absolute` поверх строки сохраняется. `searchOf`/`taskListFilters` про панели не знают: состояние панели не часть фильтров, `TasksPage` получает его отдельными пропсами из маршрута.

Маршруты `/tasks/new`, `/tasks/$taskId` и запись `"/tasks/new"` в `staticAppPaths` удаляются без редиректов.

*Альтернатива:* вложенные маршруты `/tasks/$taskId` с рендером списка под панелью. Отклонено: нужен общий layout-маршрут и перенос фильтров между путями; search-параметр уже есть в проекте и проще.

### 3. `ChoiceSheet` — одиночный выбор в `ui/`

```ts
ChoiceSheet<Id extends string>({
  open, onOpenChange, title,
  items: readonly ChecklistItem<Id>[],   // тот же тип, что у ChecklistSheet, с аватаром
  current?: Id | null,                    // undefined — ничего не отмечено (массовое назначение)
  noneLabel?: string,                     // пункт «Не назначен» первым, выбирает null
  emptyText: string,
  onChoose: (id: Id | null) => Promise<string | null>,
})
```

Список кнопок; текущий отмечен галочкой и `aria-current`. Нажатие вызывает `onChoose`; пока он выполняется, пункты недоступны и панель не закрывается (`reportGuard({ dirty: false, submitting: true })`). `null` — `close()`, строка — `FormAlert`. Черновика нет, подтверждение при закрытии не нужно.

Использование: в форме создания `onChoose` пишет значение в форму и сразу возвращает `null`; в карточке — `tasks/assign` / `tasks/unassign` + инвалидация; в списке — то же для `selectedIds`, сброс выделения и тост. Список сотрудников — `employees/list` с `Avatar`, как `withAvatar` в `GroupDetailPage`.

*Альтернатива:* режим `single` у `ChecklistSheet`. Отклонено: у него форма с «Сохранить» и защитой черновика — для одного клика лишнее, а смешение двух поведений в одном компоненте усложняет оба. Поведение повторяет `ClientPickerSheet` в режиме `single`, поэтому пользователю оно уже знакомо.

### 4. Общие поля формы и значения

Все поля задачи переходят в TanStack Form, `TaskExtras` удаляется:

```ts
interface TaskFormValues {
  title: string; description: string;
  client: { id: ClientId; name: string } | null;   // имя — для показа без запроса
  dueDate: Instant | null; dueDateEnd: Instant | null;
}
// только создание:
interface TaskCreateValues extends TaskFormValues {
  assignee: EmployeeId | null;
  attachments: readonly UploadResponse[];
}
```

`TaskFormFields` рисует заголовок, описание, клиента (имя + «Выбрать»/«Изменить» + «Убрать»), срок и окончание. Панели выбора клиента и исполнителя передаются в `EditSheetForm.nested`. Значения из панелей пишутся через `form.setFieldValue`, поэтому `dirty` — это `state.isDirty`, как в остальных панелях, а выбранный клиент, исполнитель или загруженный файл тоже защищены подтверждением.

`TaskEditSheet` берёт `defaultValues` из `TaskDetailResponse` (`clientId` + `clientName` → `client`) и отправляет в `tasks/update` все поля формы. Так `clientId` больше не теряется: он всегда часть формы, а не значение «как было».

После создания — инвалидация `tasks/list` и закрытие панели (`create` убирается из адреса). После редактирования — инвалидация `tasks/detail` этой задачи и `tasks/list`.

*Альтернатива:* оставить `TaskExtras` вне формы и считать `dirty` вручную. Отклонено: два источника состояния и самописное сравнение там, где TanStack Form уже даёт `isDirty`.

### 5. Ошибки в карточке

Ошибки мгновенных действий (статус, вложения) показываются `FormAlert` в начале тела карточки и сбрасываются при следующем действии. Ошибку смены исполнителя показывает сама `ChoiceSheet`, панель остаётся открытой.

## Risks / Trade-offs

- [Три уровня вложенности: карточка → редактирование → выбор клиента] → `EditSheet` уже поддерживает стопку; на узком экране каждая панель во всю ширину, под ней видна предыдущая только на широком. Покрыть тестом открытие и закрытие верхней панели без закрытия нижних.
- [Загруженный в форме создания файл не удаляется при отмене] → как и сейчас; upload остаётся «висящим». Вне объёма.
- [Сломанные закладки `/tasks/{id}`] → внешних ссылок нет, раздел недавно перенесён; принимаем.
- [Привязанный клиент заархивирован] → в форме показывается по имени из `tasks/detail`, «Убрать» работает; выбрать заново архивного нельзя — панель выбора показывает только неархивных.
- [Задача после смены статуса выпадает из отфильтрованного списка] → карточка открыта по `task` из адреса и не зависит от того, есть ли задача в текущей выборке.

## Migration Plan

Только фронтенд, контракты не меняются — обычный деплой, откат — откатом коммита.
