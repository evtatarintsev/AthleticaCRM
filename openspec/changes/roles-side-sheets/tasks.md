## 1. Панель роли

- [x] 1.1 Создать `web/src/settings/roles/RoleSheet.tsx`: `EditSheet` + `EditSheetForm`, название и права в `defaultValues` формы (права — `form.Field` массива `UserPermission`), `dirty`/`submitting` из состояния формы, `useEditSheet().close()` после успешного `onSave`, ошибка сервера — `FormAlert`; перенести `RoleTerms`; удалить `RoleDialog.tsx`. Проверка: `npm run check` в `web/` проходит
- [x] 1.2 Тест `RoleSheet`: пустое название показывает ошибку без запроса; отметка права и закрытие показывают подтверждение; ошибка сервера оставляет панель открытой с сообщением. Проверка: `npx vitest run src/settings/roles`

## 2. Панель списка ролей в настройках

- [x] 2.1 Создать `web/src/settings/roles/RolesSheet.tsx` по образцу `CustomFieldsSheet`: `EditSheet size="lg"`, кнопка «Добавить роль», карточки ролей с ярлыками прав, заглушка загрузки, ошибка, пустое состояние; вложенный `RoleSheet` с состоянием `editing` отдельно от `open`; создание/изменение через `employees/roles/create|update` и инвалидацию `employees/roles`. Удалить `RolesPage.tsx`. Проверка: `npm run check`
- [x] 2.2 Добавить `"roles"` в `SettingsPanelSchema`, пункт «Роли» в `SettingsPage.tsx` перевести на `panel("roles")`, зарегистрировать `SHEETS.roles`. Проверка: пункт «Роли» открывает панель поверх `/settings`, адрес `/settings?panel=roles`
- [x] 2.3 Удалить `rolesRoute` и `"/settings/roles"` из `staticAppPaths` в `web/src/app/router.tsx`; поправить `router.test.tsx` и `SettingsPage.test.tsx`. Проверка: `npm run check`
- [x] 2.4 Переписать `RolesPage.test.tsx` в `RolesSheet.test.tsx` на `/settings?panel=roles`: ярлыки прав, пустое состояние, создание роли с правами, изменение роли. Проверка: `npx vitest run src/settings`

## 3. Создание роли из панели сотрудника

- [x] 3.1 В `web/src/employees/EmployeeSheet.tsx`: добавить `"new-role"` в `EmployeePicker`, рендерить `RoleSheet` (создание) в слоте `nested`; `FormSection` научить показывать несколько действий; в секции «Роли» кнопка «Добавить роль» при пустом и непустом списке, удалить `Link` на `/settings/roles`; сохранение — `employees/roles/create`, инвалидация `employees/roles`, `form.setFieldValue("roleIds", ...)`. Проверка: `npm run check`
- [x] 3.2 Тесты в `EmployeeSheet.test.tsx`: заменить проверку ссылки при пустых ролях на кнопку; при отсутствии ролей создание роли из панели нового сотрудника выбирает её и сохраняет введённое имя, `employees/create` не вызывается; при редактировании новая роль добавляется к уже выбранным; отмена панели роли ничего не меняет; закрытие панели сотрудника после создания роли спрашивает подтверждение. Проверка: `npx vitest run src/employees`

## 4. Завершение

- [x] 4.1 Убрать неиспользуемые ключи словарей `ru.ts`/`en.ts`, если после переноса такие остались; в `web/` выполнить `npm run format` и `npm run check` — оба проходят
- [x] 4.2 Вручную в превью: настройки → «Роли» открывает панель, создание и изменение роли во вложенной панели, Esc с изменёнными правами спрашивает подтверждение; панель нового сотрудника → «Добавить роль» → роль выбрана, имя сотрудника на месте; то же в редактировании поверх карточки (четыре уровня панелей)
