## 1. База данных

- [x] 1.1 Добавить Liquibase-миграцию: индекс `conversations (org_id, last_message_at DESC)` и `conversation_read_state.last_read_at = now()` для существующих бесед с сообщениями; проверить, что `./gradlew test` поднимает схему без ошибок

## 2. Прочитанность (команда)

- [x] 2.1 Добавить в `Conversations` поиск беседы клиента без создания (`byClient(): Conversation?`) и тест в `domain/`, что пустой клиент даёт `null` и новая запись не появляется
- [x] 2.2 Сделать `DbConversationReadStates.markRead` монотонным (`GREATEST`) и покрыть тестом: более ранняя отметка не сдвигает горизонт назад
- [x] 2.3 Добавить usecase `markConversationRead(clientId, upTo)` в `usecases/messaging`: `CLIENT_NOT_FOUND` для чужого/несуществующего клиента, no-op без беседы; тест в `usecases/` на все три случая и на сообщение, пришедшее после `upTo` (остаётся непрочитанным)

## 3. Проекция «Входящих» (чтение)

- [x] 3.1 Добавить в `shared` схемы `InboxFilterSchema`, `InboxListRequest`, `InboxListResponse`, sealed `InboxItemSchema` (`ClientConversationItemSchema`, `UnresolvedChatItemSchema`), `InboxCounterResponse`, `MarkConversationReadRequest` с KDoc; проверить `./gradlew compileKotlin`
- [x] 3.2 Создать `read/messaging/InboxView` + `InboxQuery` и `DbInboxView.list()` по design §2 (UNION ALL ключей → фильтр/сортировка/LIMIT → LATERAL для строк страницы, отдельный COUNT); зарегистрировать в `ReadViews`
- [x] 3.3 Реализовать `DbInboxView.counter()` (беседы с непрочитанным + неразобранные чаты)
- [x] 3.4 Написать DB-тест `read/messaging/DbInboxViewTest`: общая сортировка бесед и чатов, исходящее последнее сообщение, клиент без сообщений и пустая беседа не попадают, изоляция организаций, фильтры `ALL/UNREAD/UNRESOLVED`, пагинация и total, значение счётчика

## 4. Маршруты

- [x] 4.1 Добавить в `MessagingRoutes` `GET /messaging/inbox/list` (нормализация `limit.coerceIn`, фильтр по умолчанию `ALL`), `GET /messaging/inbox/counter`, `POST /messaging/conversation/read`; проверить `./gradlew build`
- [x] 4.2 Удалить `GET /messaging/triage/list`, `InboundTriageView`/`DbInboundTriageView`, `TriageListResponse` и их тест (`TriageChatSchema` перенести в `UnresolvedChatItemSchema` или переиспользовать); проверить, что `./gradlew build` проходит и нигде нет ссылок на удалённое
- [x] 4.3 Перегенерировать контракты `cd web && npm run contracts` и убедиться, что `web/src/api/generated/` содержит новые схемы

## 5. Web: общий компонент беседы

- [x] 5.1 Выделить из `ConversationPage` компонент `ConversationThread({ api, clientId })` (лента, выбор канала/адреса, ввод, отправка); `ConversationPage` использует его без изменения поведения — существующие тесты проходят
- [x] 5.2 Добавить в `ConversationThread` опрос ленты (`refetchInterval` 15 с, без фона) и отметку прочтения по design §3 (видимая вкладка, `upTo` растёт); vitest на то, что отметка уходит с временем последнего показанного сообщения и не повторяется с тем же `upTo`

## 6. Web: страница «Входящие»

- [x] 6.1 Описать zod-схему search-параметров `/inbox` (`filter`, `client` | `chat`) и подключить в `router.tsx`; проверить, что прямая ссылка `/inbox?client=<id>` открывает беседу
- [x] 6.2 Переделать `InboxPage` в две панели: список из `/messaging/inbox/list` с опросом 15 с, фильтры «Все/Непрочитанные/Неразобранные», строки обоих видов (исчерпывающий `switch`), бейдж непрочитанных, «загрузить ещё»
- [x] 6.3 Правая панель: для беседы клиента — шапка со ссылкой на карточку + `ConversationThread`; для неразобранного чата — текущие `ChatPanel` и привязка; после привязки инвалидировать список и счётчик и перейти на `?client=<id>`
- [x] 6.4 Узкий экран: список или беседа с кнопкой «назад»; проверить в браузере на 375px
- [x] 6.5 Счётчик у пункта навигации «Входящие» из `/messaging/inbox/counter` (опрос 30 с, скрыт при нуле, инвалидация после отметки прочтения и привязки)
- [x] 6.6 Строки в `ru.ts`/`en.ts` (фильтры, пустые состояния по фильтрам, «Вы:» для исходящего, «Открыть карточку», «Назад»)
- [x] 6.7 Обновить `InboxPage.test.tsx`: смешанный список, фильтры, ответ из открытой беседы, привязка чата → открыта беседа клиента

## 7. Проверка

- [x] 7.1 Запустить `./gradlew build`, `./gradlew ktlintFormat`, в `web/` — `npm run format` и `npm run check`; всё зелёное
- [x] 7.2 Пройти сценарий вручную в dev-окружении: входящее от привязанного клиента появляется во «Входящих» без перезагрузки, счётчик растёт, открытие беседы обнуляет его, ответ уходит со страницы, привязка неразобранного чата оставляет беседу открытой
