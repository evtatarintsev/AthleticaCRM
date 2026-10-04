## 1. Основа

- [x] 1.1 Добавить в `ChecklistSheet` пропсы `searchLabel` и `submitLabel` по design.md §4: поле поиска над списком, фильтр по `name` без учёта регистра, «Ничего не найдено» при пустом результате, черновик отметок не зависит от фильтра; тесты в `ChecklistSheet.test.tsx`: поиск фильтрует список, отмеченная и скрытая поиском запись уходит в `onSubmit`, без `searchLabel` поля поиска нет
- [x] 1.2 Создать тип `EmployeeFormValues` и zod-схему формы по design.md §3 (расширение `employeeContactSchema`), функции сборки значений из `EmployeeDetailResponse` (право в обоих списках → `revoke`) и раскладки `permissions` в `grantedPermissions`/`revokedPermissions`; юнит-тесты на обе функции
- [x] 1.3 Добавить строки RU/EN: заголовки панелей прав, ролей и филиалов, три состояния права, пометка «из роли», сводки секций («Выдано: N, отозвано: M», «Права определяются ролями», «не выбраны»), поиск прав, ролей, филиалов, «Ничего не найдено»; проверить `npm run check` (typecheck словарей)

## 2. Панель прав

- [x] 2.1 Создать `web/src/employees/PermissionsSheet.tsx` по design.md §5: `EditSheet md` + `EditSheetForm` «Готово», поиск по переведённым названию и описанию, переключатель из трёх радиокнопок на право, пометка для прав из `rolePermissions`, `dirty` — сравнение черновика с `value`; тесты в `PermissionsSheet.test.tsx`: переключение «Выдано» → «Отозвано» отдаёт в `onApply` только отзыв, поиск по слову из описания оставляет право, пустой поиск — сообщение, право из роли помечено, закрытие без «Готово» не вызывает `onApply`

## 3. Панель сотрудника

- [x] 3.1 Создать `web/src/employees/EmployeeSheet.tsx` по design.md §1, §3, §6: режимы `create`/`edit`, фото (`AvatarPicker`) вверху, имя/телефон/email, секции «Роли», «Права», «Доступ к филиалам» со сводками и «Изменить» → вложенные `ChecklistSheet` с поиском / `PermissionsSheet` в `nested`; загрузка `employees/roles` и `branches/list` со скелетоном; ссылка на настройки ролей при пустом списке ролей; `allBranchesAccess`: `false` при создании, значение сотрудника при редактировании, пометка в секции филиалов при включённом флаге; `dirty` = `state.isDirty`
- [x] 3.2 Удалить `EmployeeCreatePage.tsx`, `EmployeeEditPage.tsx`, `EmployeeFormFields.tsx`, `employeeExtras.ts`; перенести тесты из `EmployeeForm.test.tsx` в `EmployeeSheet.test.tsx` и дополнить: пустой email не отправляет запрос, создание с ролью шлёт её в `employees/create` и закрывает панель, редактирование сотрудника с флагом «все филиалы» отправляет `allBranchesAccess: true`, выбор права в панели прав попадает в `grantedPermissions`, Esc после выбора роли показывает подтверждение, ошибка сервера оставляет панель с данными

## 4. Страницы и маршруты

- [x] 4.1 Добавить `validateSearch` для `/employees` (`create`) и `/employees/$employeeId` (`edit`) по design.md §2; в `EmployeesPage` смонтировать `EmployeeSheet mode=create`, кнопку «Добавить сотрудника» сделать `<Link search={{ create: true }} replace>`; после создания — инвалидация `employees/list`; тесты в `EmployeeSheet.test.tsx`: кнопка открывает панель и добавляет `create` в адрес, закрытие убирает его
- [x] 4.2 В `EmployeeDetailPage` смонтировать `EmployeeSheet mode=edit` с загруженным сотрудником, «Редактировать» — `<Link search={{ edit: true }} replace>`; после сохранения — инвалидация `employees/detail` и `employees/list`; тест в `EmployeeSheet.test.tsx`: «Редактировать» открывает панель с текущими данными, сохранение закрывает её и карточка показывает новые данные
- [x] 4.3 Удалить из `router.tsx` маршруты `/employees/new`, `/employees/$employeeId/edit` и `"/employees/new"` из `staticAppPaths`; обновить `router.test.tsx`: `/employees?create=true` открывает список с панелью, `/employees/<id>?edit=true` — карточку с панелью, `/employees/new` — «не найдено»; проверить поиском, что `EmployeeCreatePage`, `EmployeeEditPage`, `/employees/new`, `$employeeId/edit` больше не упоминаются

## 5. Проверка

- [x] 5.1 В `web/` выполнить `npm run format` и `npm run check` (typecheck, eslint, prettier, vitest) — без ошибок
- [x] 5.2 Запустить приложение и в браузере пройти: создание сотрудника с фото, ролью, выданным правом и филиалом; редактирование из карточки с перезагрузкой при открытой панели; поиск в панелях ролей, прав, филиалов; пометка «из роли» меняется после смены ролей в той же форме; Esc во вложенной панели закрывает только её; на ширине 360 px панели во всю ширину, без горизонтальной прокрутки, переключатель прав помещается в строку

## 6. Карточка в панели

- [x] 6.1 Перенести `EmployeeDetailPage` в панель `EmployeeCardSheet` по design.md §1 (загрузка `employees/detail` только при открытой панели, ошибка загрузки — `FormAlert`, «Отправить доступ», «Редактировать» → вложенная `EmployeeSheet mode=edit`); схема `EmployeeListSearchSchema` с `employee`/`edit`/`create` по design.md §2; строки списка — ссылки с `employee`; удалить маршрут `/employees/$employeeId`; тесты в `EmployeeSheet.test.tsx` и `EmployeesPage.test.tsx`: клик по строке открывает карточку и добавляет `employee` в адрес, Esc закрывает её и убирает из адреса, «Редактировать» открывает форму поверх карточки, Esc в форме закрывает только её, сохранение обновляет карточку; в `router.test.tsx`: `/employees/<id>` — «не найдено», `/employees?employee=not-a-uuid` — список без панели и без запроса карточки
- [x] 6.2 В `web/` выполнить `npm run format` и `npm run check` — без ошибок; в браузере: открытие карточки из списка, редактирование поверх карточки, перезагрузка с `employee` и `edit` в адресе
