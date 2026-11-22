# Tasks

## 1. Схема БД

- [x] 1.1 Добавить миграцию `client_contacts`: `label`, `is_primary`, `position`, заполнение существующих строк и частичный индекс `uq_client_contacts_primary` (design, решение 1); зарегистрировать в changelog. Проверка: `./gradlew test --tests "*DbClientContactsTest"` поднимает схему без ошибок

## 2. Домен контактов

- [x] 2.1 Расширить `ClientContact` полями `label: String?`, `isPrimary: Boolean` с KDoc; поправить все места создания (`ClientsRoutes.toDomain`, `ClientDrafts`, `DbClientChannelsView`, тесты). Проверка: `./gradlew compileKotlin compileTestKotlin`
- [x] 2.2 Написать чистую функцию нормализации набора контактов (обрезка комментария, основной внутри типа, `CONTACT_PRIMARY_DUPLICATE`, порядок по типам с основным первым) и unit-тест на все сценарии `specs/client-contacts`. Проверка: тест проходит
- [x] 2.3 Вызывать нормализацию в `DbClientContacts.replace`, писать `label`, `is_primary`, `position`; `byClient/byClients` читают новые поля и сортируют по `position`. Расширить `DbClientContactsTest`: сохранённый набор возвращается нормализованным, два основных отклоняются и не меняют прежние контакты, удаление основного делает основным следующий. Проверка: тест проходит

## 3. API и проекции

- [x] 3.1 Добавить `label`/`isPrimary` в `ClientContactInput` (с умолчаниями) и `ClientContactSchema`; маппинг в `ClientsRoutes` (`toDomain`, `toSchema`). Проверка: тест маршрута/usecase создания клиента со старым форматом `{type, value}` проходит
- [x] 3.2 `DbClientListView`: `jsonb_agg` отдаёт `label`, `isPrimary`, сортировка по `position`; дополнить `DbClientListViewTest`. Проверка: тест проходит
- [x] 3.3 `DbClientChannelsView`: контакты канала с `label`, `isPrimary`, сортировка по `position`; дополнить `DbClientChannelsViewTest`. Проверка: тест проходит
- [x] 3.4 `ClientDrafts`: передавать `label` из черновика, `isPrimary` не выставлять; проверить существующие тесты ассистента. Проверка: `./gradlew test --tests "*ClientDrafts*"`
- [x] 3.5 Перегенерировать контракты `cd web && npm run contracts`. Проверка: `web/src/api/generated/contracts.ts` содержит новые поля, `npm run check` проходит типизацию

## 4. Веб: форма контактов

- [x] 4.1 `clientContacts.ts`: `ContactFormEntry` с `label`/`isPrimary`; чистые функции добавления строки типа, отметки основного, удаления с переносом основного; `contactEntriesOf`/`contactInputsOf` с новыми полями; vitest-тесты на сценарии `specs/web-client/client-contacts` (флажок, удаление основного, второй телефон). Проверка: тест проходит
- [x] 4.2 Вынести иконку типа контакта из `card/ClientContactList.tsx` в общую функцию для формы и списка. Проверка: `npm run check`
- [x] 4.3 `ClientContactsFields.tsx`: строка = иконка + значение + комментарий + флажок «основной» (если строк типа > 1) + удалить; без селекта типа; кнопки «+ Телефон», «+ Email», «+ Ещё» (dropdown-menu: Telegram, VK, Facebook) с фокусом в новой строке; строки i18n в `ru.ts`/`en.ts`. Обновить `ClientFormPage.test.tsx`: пустая форма с одной строкой телефона, добавление через «+ Ещё», выбор основного уходит в запрос. Проверка: тест проходит

## 5. Веб: отображение

- [x] 5.1 `ClientContactList`: подпись — комментарий или название типа, пометка «основной» при нескольких контактах типа; тест в `ClientDetailPage.test.tsx` на сценарий «Телефоны ребёнка». Проверка: тест проходит
- [x] 5.2 `ClientCardHeader`: основной телефон вместо первого; тест на сценарий «Основной телефон не первый». Проверка: тест проходит
- [x] 5.3 `ConversationThread`: адрес по умолчанию — контакт с `isPrimary`, иначе первый; тест на сценарий «SMS на основной телефон» (`InboxPage.test.tsx` или тест панели «Чат»). Проверка: тест проходит

## 6. Связанный change и проверка целиком

- [x] 6.1 В `openspec/changes/client-mobile-app/specs/client-app-access/spec.md` дописать: в списке доступов клиента рядом с номером показывается комментарий к нему. Проверка: `openspec validate client-mobile-app`
- [x] 6.2 Прогнать обязательные проверки: `./gradlew build`, `./gradlew ktlintFormat`, в `web/` — `npm run format` и `npm run check`. Проверка: всё зелёное, `git status` не показывает несгенерированных изменений контрактов

## 7. Доработки по ревью

- [x] 7.1 Лимит комментария 100 символов (`CONTACT_LABEL_TOO_LONG`) в `normalized()`, `maxLength` на форме. Проверка: `ClientContactsNormalizedTest`
- [x] 7.2 Отложенный триггер `ck_client_contacts_one_primary` в миграции; фикстуры с прямой вставкой отмечают основной. Проверка: `DbClientContactsTest`
- [x] 7.3 Помощник: `label`/`isPrimary` из запроса, нормализация в `prepare`, подсказка модели, комментарий в карточке подтверждения. Проверка: `AssistantServiceTest`, `Assistant.test.tsx`
