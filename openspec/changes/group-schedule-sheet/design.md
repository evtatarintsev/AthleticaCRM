## Context

Мотивация — в proposal.md, требования — в `specs/group-schedule-editor/spec.md`. На что опирается решение:

- `web/src/groups/GroupScheduleDialog.tsx` — единственное место, где редактор используется (`GroupDetailPage`). Контракт уже подходит панели: `onSave(cards, effectiveFrom) => Promise<string | null>` (`null` — успех, строка — текст ошибки), как у `ChecklistSheet` и `ClientPickerSheet`.
- `web/src/ui/EditSheet.tsx`: `EditSheet` (панель справа, защита несохранённых изменений через `reportGuard`, клик мимо не закрывает, содержимое монтируется заново при каждом открытии) и `EditSheetForm` (прокручиваемое тело, закреплённые «Отмена» / «Сохранить»). Кнопка отправки в `EditSheetForm` блокируется только на время `submitting`.
- `web/src/groups/groupSchedule.ts` — чистые функции редактора (`slotsToCards`, `cardsToSlotInputs`, `cardErrors`, `newCard`) и тип `SlotCard` с локальным `id`.
- Ширина диалога (`sm:max-w-md`) совпадает с `EditSheet size="md"`, так что вёрстка карточек на узком экране не меняется.

## Goals / Non-Goals

**Goals:**
- Перенести редактор в `EditSheet` без изменения его тела и логики проверок.
- Сохранить поведение «Сохранить недоступна при ошибках карточек», не ломая остальные панели.

**Non-Goals:**
- Обобщать редактор расписания для других экранов.
- Переводить редактор на TanStack Form, как формы настроек.

## Decisions

### 1. `GroupScheduleSheet` вместо `GroupScheduleDialog`

```
GroupScheduleSheet                      // EditSheet title="Расписание" size="md"
  open, onOpenChange
  initialCards, scheduleChangeAt, halls, onSave
  +-- ScheduleEditorContent             // монтируется при каждом открытии
        state: cards, effectiveFrom, failure, saving
        EditSheetForm dirty submitting submitDisabled onSubmit
          FormAlert | дата | предупреждение | noHalls | SlotCardEditor[] | «Добавить карточку»
```

Файл переименовывается в `GroupScheduleSheet.tsx`, `SlotCardEditor`, `hallIdOf`, `errorMessage` переезжают без изменений. Успех — `useEditSheet().close()`, ошибка — `FormAlert`, карточки остаются. Состояние инициализируется при монтировании: `EditSheet` монтирует содержимое заново при каждом открытии, поэтому сброс через `wasOpen` не нужен. В `GroupDetailPage` условный рендер `{scheduleOpen && <GroupScheduleDialog …/>}` заменяется на постоянно смонтированную панель с `open={scheduleOpen}`, как у `ChecklistSheet`.

*Альтернатива:* оставить `Dialog` и добавить в него подтверждение закрытия. Отклонено: дублирует механизм `EditSheet` и не решает разнобой стилей.

### 2. `submitDisabled` в `EditSheetForm`

`EditSheetForm` получает необязательный `submitDisabled?: boolean` (по умолчанию `false`). Кнопка «Сохранить» — `disabled={submitting || submitDisabled}`; отправка по Enter при `submitDisabled` игнорируется (обработчик `onSubmit` формы не вызывает колбэк). Остальные панели параметр не передают, их поведение не меняется.

Редактор передаёт `submitDisabled={cardErrors(cards).size > 0}`.

*Альтернатива:* «Сохранить» всегда доступна, проверка по нажатию — как в формах на TanStack Form. Отклонено: ошибки карточек и так видны сразу, а требование «Проверки перед сохранением» и его сценарии пришлось бы переписывать.

### 3. Изменённость — сравнение карточек по значению

В `groupSchedule.ts` добавляется чистая функция `cardsChanged(initial, cards): boolean`: списки сравниваются попарно по порядку, по дням (как множеству), `startAt`, `endAt`, `hallId`; локальный `id` не учитывается. Разная длина — изменено. Порядок значим: в редакторе карточки не переставляются, новые добавляются в конец, поэтому добавить и удалить ту же карточку — снова исходный список.

`dirty = cardsChanged(initialCards, cards) || effectiveFrom !== today`, где `today` вычислен при монтировании.

*Альтернатива:* сравнение по итоговым слотам (`cardsToSlotInputs`). Отклонено: пустая или недозаполненная карточка не даёт слотов, и начатая работа закрывалась бы без вопроса; перегруппировка дней между карточками при этом тоже считалась бы «без изменений», что пользователю неочевидно.

## Risks / Trade-offs

- [`todayLocalDate()` меняется после полуночи, пока панель открыта] → `today` фиксируется при монтировании содержимого; сравнение с ним, а не с текущей датой.
- [Новый параметр в общем `EditSheetForm`] → необязательный, по умолчанию прежнее поведение; покрыт тестом редактора расписания.
- [UI редактора сейчас не покрыт тестами] → добавляются тесты в `GroupDetailPage.test.tsx`: открытие панели, блокировка «Сохранить», подтверждение при закрытии, ошибка сервера.

## Migration Plan

Только фронтенд, контракты не меняются — обычный деплой, откат — откатом коммита.
