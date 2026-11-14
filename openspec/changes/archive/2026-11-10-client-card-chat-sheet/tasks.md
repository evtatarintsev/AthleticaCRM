## 1. Панель переписки

- [x] 1.1 Превратить `web/src/messaging/ConversationPage.tsx` в `ConversationSheet.tsx`: `EditSheet` размера `lg` с заголовком «Чат» (`messaging.title` → «Чат» / «Chat»), тело — лента `flex-1` с прокруткой и закреплённая внизу строка канала и ввода; убрать `PageHeader` и `100vh`; проверка — `npm run check` в `web/` проходит
- [x] 1.2 В теле панели сообщать `reportGuard({ dirty: draft.trim() !== "", submitting: sending })`, ставить фокус в поле ввода после загрузки и прокручивать ленту к последнему сообщению при изменении их числа; проверка — тесты `ConversationSheet` / `ClientDetailPage`: фокус в поле ввода, Esc с текстом спрашивает подтверждение, без текста закрывает сразу

## 2. Карточка клиента

- [x] 2.1 В `ClientCardHeader` заменить ссылку на `/clients/$clientId/messages` кнопкой «Чат» с колбэком `onOpenChat`; в `ClientMessagesSection` ссылку «Открыть чат» — кнопкой с колбэком `onOpen`; i18n: `messaging.open` → «Чат» / «Chat», `clientCard.openConversation` → «Открыть чат» / «Open chat»; проверка — `rg '/messages"' web/src` ничего не находит
- [x] 2.2 В `ClientDetailPage` хранить `chatOpen`, передать колбэки и рендерить `ConversationSheet`; проверка — тесты в `ClientDetailPage.test.tsx`: «Чат» и «Открыть чат» открывают панель поверх карточки без смены адреса; после отправки сообщение видно в блоке «Сообщения»

## 3. Удаление страницы

- [x] 3.1 Удалить маршрут `/clients/$clientId/messages` из `web/src/app/router.tsx`; проверка — `npm run check` проходит

## 4. Проверка

- [x] 4.1 Прогнать `npm run format` и `npm run check` в `web/` — без ошибок
