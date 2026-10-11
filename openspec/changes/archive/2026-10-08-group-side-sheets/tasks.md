## 1. Общие части

- [x] 1.1 Вынести `ChipsList` и `withAvatar` из `web/src/groups/GroupDetailPage.tsx` в отдельный модуль группы (например `groups/GroupChips.tsx`), карточку перевести на него; заголовок секции с «Изменить» — общий (`FormSection` из `EmployeeSheet` в `ui/`, если вынос тривиален, иначе локальный в `groups/`). Проверка: `npx vitest run src/groups` — тесты карточки проходят без изменений

## 2. Панель создания группы

- [x] 2.1 Создать `web/src/groups/GroupCreateSheet.tsx`: `EditSheet` + `EditSheetForm` с подписью «Создать»; `useForm` с `{ name, disciplineIds, employeeIds }`, название — `TextField` с обязательной валидацией (trim); секции «Дисциплины» и «Тренеры» с чипами и «Изменить»; вложенные `ChecklistSheet` (дисциплины филиала, тренеры с аватарами) в слоте `nested` с «Готово» и `form.setFieldValue`; сохранение `groups/create` с `uuidv7`, инвалидация `groups/list`, `useEditSheet().close()`, переход на `/groups/$groupId`; ошибка сервера — `FormAlert`. Проверка: `npm run check` в `web/`
- [x] 2.2 В `groupListSearch.ts` добавить `create: z.literal(true).optional().catch(undefined)`; в `GroupsPage.tsx` кнопка «Новая группа» открывает панель через search (с сохранением фильтров), смена фильтров не теряет `create`, закрытие убирает только `create`; рендерить `GroupCreateSheet`. Проверка: `/groups?create=true` открывает панель поверх списка, фильтры в адресе сохраняются
- [x] 2.3 Тест `GroupCreateSheet` (через `/groups?create=true`): пустое название — ошибка без запроса; выбор тренеров через «Изменить» → «Готово» показывает чипы и не вызывает API; «Создать» отправляет `groups/create` с названием и выбором и открывает карточку; ошибка сервера оставляет панель открытой с данными; закрытие после выбора тренера спрашивает подтверждение. Проверка: `npx vitest run src/groups`

## 3. Панель изменения названия

- [x] 3.1 Создать `web/src/groups/GroupRenameSheet.tsx`: `EditSheet` + `EditSheetForm`, только «Название» с текущим значением; сохранение `groups/edit` с `disciplineIds`/`employeeIds` из загруженной карточки, инвалидация `groups/list` и `groups/detail`, тост, закрытие. В `GroupDetailPage.tsx` действие «Изменить» в заголовке открывает панель (локальное состояние) вместо `Link` на `/groups/$groupId/edit`. Проверка: `npm run check`
- [x] 3.2 Тесты в `GroupDetailPage.test.tsx`: переименование отправляет `groups/edit` с прежними дисциплинами и тренерами, заголовок обновляется, показан тост; пустое название — ошибка без запроса; Esc без изменений закрывает панель без подтверждения. Проверка: `npx vitest run src/groups`

## 4. Удаление страниц и маршрутов

- [x] 4.1 Удалить `GroupCreatePage.tsx`, `GroupEditPage.tsx`, `GroupFormPage.tsx`; в `web/src/app/router.tsx` удалить `groupNewRoute`, `groupEditRoute` и `"/groups/new"` из `staticAppPaths`; поправить `router.test.tsx` / `router.types.test.ts`, добавить проверку, что `/groups/new` и `/groups/<id>/edit` показывают «не найдено». Проверка: `npx vitest run src/app`
- [x] 4.2 Убрать ключи словарей `ru.ts`/`en.ts`, ставшие неиспользуемыми (`groups.removeChip`, `groups.detail.addDiscipline`, `groups.detail.addEmployee` и т. п.), добавить новые (тост переименования и др.) в оба словаря. Проверка: `npm run check`

## 5. Завершение

- [x] 5.1 В `web/` выполнить `npm run format` и `npm run check` — оба проходят
- [ ] 5.2 Вручную в превью: «Новая группа» на отфильтрованном списке → панель, выбор дисциплин и тренеров во вложенных панелях, «Создать» → карточка новой группы; на карточке «Изменить» → переименование, тренеры и дисциплины не изменились; Esc с изменениями спрашивает подтверждение; `/groups/new` → «не найдено»
