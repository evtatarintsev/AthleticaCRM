## Context

Мотивация — в proposal.md, требования — в `specs/web-client/group-disciplines-trainers/spec.md`. На что опирается решение:

- `web/src/ui/EditSheet.tsx`: `EditSheet` (панель справа, защита несохранённых изменений через `reportGuard`, вложенность) и `EditSheetForm`. `EditSheetForm` уже содержит прокручиваемое тело, «Отмена» / «Сохранить» и сообщает `dirty` / `submitting` панели.
- `ClientPickerSheet` (`web/src/clients/`) — панель выбора клиентов на `EditSheet` с контрактом `onSubmit(...) => Promise<string | null>`: `null` — успех, строка — текст ошибки.
- `MultiSelectPicker` (`web/src/ui/`) — шторка снизу с черновиком и «Готово»; кроме карточки группы используется в `GroupFormPage` и фильтрах.
- Сервер: `groups/set-disciplines` и `groups/set-employees` принимают полный список id и заменяют набор целиком; `disciplines/list` и `employees/list` уже загружаются в карточке (`useGroupDisciplines`, `useGroupEmployees`).

## Goals / Non-Goals

**Goals:**
- Один компонент панели с чекбоксами для двух секций карточки, без доменных зависимостей.
- Тот же контракт результата, что у `ClientPickerSheet`: ошибка API показывается в панели, а не тостом.

**Non-Goals:**
- Замена `MultiSelectPicker` в форме группы и фильтрах — он остаётся как есть.
- Обобщение `ClientPickerSheet` и новой панели в один компонент.

## Decisions

### 1. `ChecklistSheet<Id>` в `web/src/ui/` на базе `EditSheet` + `EditSheetForm`

```
ChecklistSheet<Id extends string>
  open, onOpenChange
  title
  items: readonly ChecklistItem<Id>[]          // { id, name, avatar?: ReactNode }
  selected: readonly Id[]                      // текущий набор группы
  emptyText
  onSubmit: (ids: readonly Id[]) => Promise<string | null>
```

Содержимое — `EditSheetForm` с `dirty` = «черновик отличается от `selected` как множество», `submitting` на время `onSubmit`, телом — список `label` + `checkbox`, как в `ClientPickerSheet`. Успех — `useEditSheet().close()`, ошибка — `FormAlert` над списком, черновик сохраняется. Черновик инициализируется из `selected` при монтировании; `EditSheet` монтирует содержимое заново при каждом открытии, поэтому синхронизация через `wasOpen`, как в `MultiSelectPicker`, не нужна.

Аватар тренера передаётся слотом `avatar: ReactNode` (`<Avatar … />` собирает карточка): `ui/` не знает про `ApiClient` и загрузки.

*Альтернативы:*
- Переделать `MultiSelectPicker` на `EditSheet`. Отклонено: он же служит фильтрам, где «Сбросить» / «Готово» без сохранения на сервер и без защиты от закрытия — другая семантика.
- Переиспользовать `ClientPickerSheet`. Отклонено: там выбор добавляется к набору (уже выбранные заблокированы), здесь набор заменяется целиком и отметки снимаются; плюс серверный поиск и порции здесь не нужны.

### 2. Кнопка «Изменить» в заголовке секции

Заголовок секций «Дисциплины» и «Тренеры» оформляется так же, как у «Расписания»: `h2` слева, `Button variant="ghost" size="sm"` справа. Подпись — общая строка «Изменить» (`groups.detail.editSchedule` переименовывается/обобщается до `groups.detail.editSection` либо добавляется отдельная строка — на усмотрение реализации). Строки `groups.detail.addDiscipline` / `addEmployee` остаются: ими пользуется `GroupFormPage`.

### 3. Чипы только для чтения

`ChipsList` удаляется из карточки; чипы дисциплин — `span` с именем, тренеров — `span` с `Avatar` и именем, без кнопок. Пустой набор — текст `groups.detail.disciplinesEmpty` / `employeesEmpty`. `MultiSelectPicker` и состояние `picker` из карточки уходят, вместо них — `useState<"disciplines" | "employees" | null>` для открытой панели.

### 4. Сохранение

`saveDisciplines` / `saveEmployees` возвращают `Promise<string | null>` вместо тоста ошибки: ошибка — `apiErrorMessage`, успех — `refresh()` + тост «Дисциплины обновлены» / «Тренеры обновлены» (строки уже есть).

Список в панели — все записи из `disciplines/list` / `employees/list` без фильтра по `isActive`. Пока справочники грузятся, панель показывает пустой список (`emptyText`) — кнопка «Изменить» не блокируется: оба запроса стартуют вместе с карточкой и к моменту нажатия почти всегда готовы.

## Risks / Trade-offs

- [Неактивные сотрудники в списке тренеров] → осознанно: иначе сохранение молча снимало бы с группы уже назначенного неактивного тренера. Пометку «неактивен» можно добавить позже без изменения контракта панели.
- [Справочник ещё не загрузился, а пользователь нажал «Сохранить»] → черновик инициализируется из `selected`, а не из видимых записей, поэтому сохранение отправит текущий набор группы и ничего не снимет.
- [Тесты карточки завязаны на крестики и пунктирные чипы] → переписываются на «Изменить → отметки → Сохранить».

## Migration Plan

Только фронтенд, контракты не меняются — обычный деплой, откат — откатом коммита.
