## Context

`GroupCreatePage` и `GroupEditPage` — обёртки над `GroupFormPage`: название в `useForm`, дисциплины
и тренеры — в отдельных `useState`, выбор через `MultiSelectPicker` и локальный `ChipsField`.
Признак «форма изменена» выбор дисциплин и тренеров не видит.

Образцы, на которые опирается изменение:

- `EmployeeSheet` — форма в `EditSheet` + `EditSheetForm`; выбор (`roleIds`, `branchIds`) — поля
  `useForm`; вложенные `ChecklistSheet` в слоте `nested` управляются состоянием `picker`, их
  `onSubmit` делает `form.setFieldValue` и возвращает `Promise.resolve(null)` с подписью «Готово».
  Секции формы — локальный `FormSection` (заголовок + «Изменить»).
- `EmployeesPage` — открытая панель создания хранится в search-параметре `create: z.literal(true)`.
- `GroupDetailPage` — секции дисциплин и тренеров: `ChipsList` (чипы `ChecklistItem` с аватаром) и
  `ChecklistSheet`, сохраняющий набор на сервер; хелпер `withAvatar` для тренеров.

## Goals / Non-Goals

**Goals:**
- Одна форма создания, где весь выбор — состояние `useForm`, чтобы работала защита `EditSheet`.
- Вид выбора в панели создания совпадает с карточкой: те же чипы и тот же `ChecklistSheet`.

**Non-Goals:**
- Общий компонент «форма группы» на создание и правку: у правки одно поле, общего почти нет.
- Перенос `FormSection` в `ui/` как общего компонента — только если это не раздувает изменение
  (см. решение ниже).

## Decisions

### Две панели вместо одной с режимами

```
GroupsPage (/groups?create=true)          GroupDetailPage (/groups/$id)
  +- GroupCreateSheet (EditSheet md)        +- GroupRenameSheet (EditSheet md)
       EditSheetForm "Создать"                   EditSheetForm "Сохранить"
         Название                                  Название
         [Дисциплины ........ Изменить]
         [Тренеры ........... Изменить]
       nested:
         ChecklistSheet дисциплин  "Готово"
         ChecklistSheet тренеров   "Готово"
```

`GroupCreateSheet({ api, open, onOpenChange })` — `useForm` с `{ name, disciplineIds, employeeIds }`,
`picker: "disciplines" | "employees" | null`. Сохранение: `groups/create` с новым `uuidv7`,
инвалидация `groups/list`, `useEditSheet().close()`, переход на `/groups/$groupId`.

`GroupRenameSheet({ api, open, onOpenChange, group: GroupDetailResponse })` — `useForm` с `{ name }`.
Сохранение: `groups/edit` с `disciplineIds`/`employeeIds` из `group`, инвалидация `groups/list` и
`groups/detail`, тост, закрытие.

Альтернатива — одна `GroupSheet` с `initial: GroupDetailResponse | null`, скрывающая секции при
правке — отвергнута: ветвление по режиму в каждой части формы ради одного общего поля.

### Секции выбора: переиспользуем `ChipsList` карточки

`ChipsList` и `withAvatar` из `GroupDetailPage.tsx` выносятся в `groups/GroupChips.tsx` (или
аналог) и используются и карточкой, и панелью создания — так чипы выглядят одинаково. Заголовок
секции с «Изменить» в карточке и в панели — одна разметка; если вынос `FormSection` из
`EmployeeSheet` в `ui/` тривиален, переиспользуем его, иначе — локальная секция в группах.

Чипы без крестика: удаление — через снятие отметки в панели выбора, как в карточке
(`group-disciplines-trainers`). Альтернатива с крестиками на чипах (как в старом `ChipsField`)
отвергнута — два способа делать одно и то же и расхождение с карточкой.

### Панель создания в адресе списка, правка — локальное состояние карточки

`GroupListSearchSchema` получает `create: z.literal(true).optional().catch(undefined)`, как у
сотрудников. `GroupsPage` пересобирает search из фильтров через `searchOf(filters)` — параметр
`create` должен переживать смену фильтров (передавать его отдельно или мержить с текущим search);
закрытие панели убирает только `create`.

Правка на карточке — `useState`, как панели расписания и выбора: ссылка «сразу открыть
переименование» не нужна, а search-параметр у карточки пришлось бы заводить с нуля.

### Удаление маршрутов

`groupNewRoute`, `groupEditRoute` и `"/groups/new"` в `staticAppPaths` удаляются, файлы
`GroupCreatePage.tsx`, `GroupEditPage.tsx`, `GroupFormPage.tsx` — тоже. Редиректа нет: `/groups/new`
попадает в `groupRoute`, `GroupIdSchema` не парсит `new` → «не найдено»; `/groups/{id}/edit` не
совпадает ни с одним маршрутом. Ключи словаря `groups.removeChip`, `groups.detail.addDiscipline`,
`groups.detail.addEmployee` удаляются, если больше нигде не используются.

## Risks / Trade-offs

- [`groups/edit` перезаписывает дисциплины и тренеров значениями из загруженной карточки; если
  их параллельно изменили в другой вкладке, переименование вернёт старый набор] → карточка
  обновляется после каждого изменения секций, окно гонки мало; отдельный `groups/rename` на сервере
  — вне рамок (без серверных изменений), при необходимости — отдельным изменением.
- [Закладки на `/groups/new` перестают работать] → принято, как для `/settings/roles` и форм
  сотрудника.
