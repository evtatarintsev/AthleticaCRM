## Context

Мотивация — см. proposal.md. Требования — `specs/inbox/spec.md` и `specs/inbound-triage/spec.md`.

Что уже есть:

- `conversations` — одна беседа на клиента (`UNIQUE (org_id, client_id)`), поле `last_message_at`
  обновляется при добавлении сообщения (`DbConversation`). Беседа создаётся лениво
  в `Conversations.forClient`, в том числе при простом `GET /messaging/conversation`, поэтому
  существуют пустые беседы.
- `messages` (`direction`, `body`, `created_at`), индекс `(conversation_id, created_at)`.
- `conversation_read_state` (`conversation_id` PK, `org_id`, `last_read_at`) и доменный
  `ConversationReadStates` с `markRead/unreadCount` — реализованы, но ни один маршрут их не вызывает.
- Очередь разбора — `read/messaging/DbInboundTriageView` (группировка `inbound_receipts`
  в состоянии `UNRESOLVED` по `(integration_id, from_address)`), `/messaging/triage/*`.
- Web: `InboxPage` (только очередь + привязка), панель чата карточки `ConversationSheet` (лента + отправка, поверх карточки клиента), `NotificationBell` уже опрашивает сервер через `refetchInterval`.
- У клиентов нет привязки к филиалу: клиентская база общая на организацию.

## Goals / Non-Goals

**Goals:**
- Один запрос на список «Входящих» со всеми полями строки, фильтром и пагинацией.
- Переиспользовать ленту и ввод ответа между панелью чата карточки и «Входящими», а не копировать.
- Включить уже спроектированную прочитанность (`conversation_read_state`), не меняя её модель.

**Non-Goals:**
- Прочитанность на каждого сотрудника, назначение ответственного за беседу, статусы
  «закрыта/в работе».
- WebSocket/SSE-доставка; поиск по тексту сообщений.
- Ограничение видимости бесед филиалами или отдельным правом.

## Decisions

### 1. Видимость — вся организация, без нового права

Рекомендация в обсуждении была «по филиалам клиента», но у клиентов нет филиала
(`clients` без `branch_id`; клиентский список и очередь разбора тоже скоупятся только по
`org_id`). Поэтому «Входящие» видны всем сотрудникам организации — так же, как список
клиентов и карточка с перепиской. Отдельное право (`CAN_VIEW_INBOX`) не вводим: оно закрыло бы
бесед меньше, чем открыто через карточки клиентов. Если появится филиал клиента, фильтр добавится
в одну проекцию.

*Альтернатива:* фильтровать по филиалам групп, где состоит клиент. Отклонено: клиент без групп
(лид) пропал бы из «Входящих», а именно лиды чаще всего пишут первыми.

### 2. Одна проекция `InboxView` с `UNION ALL`

`read/messaging/InboxView` + `DbInboxView`, метод `list(query: InboxQuery): InboxListResponse`.
SQL — `UNION ALL` двух веток с общим набором колонок, затем `ORDER BY last_at DESC, key`
и `LIMIT/OFFSET`, плюс отдельный `COUNT` по тому же `UNION` для пагинации (шаблон
`DbClientListView`):

- ветка бесед: `conversations c JOIN clients` по `org_id`, `c.last_message_at IS NOT NULL`;
  последнее сообщение — `LEFT JOIN LATERAL (SELECT body, direction, created_at FROM messages
  WHERE conversation_id = c.id ORDER BY created_at DESC, id DESC LIMIT 1)`; непрочитанные —
  `LATERAL count(*)` входящих с `created_at > COALESCE(rs.last_read_at, 'epoch')`;
- ветка неразобранных: тот же подзапрос, что в `DbInboundTriageView`.

Фильтр (`ALL | UNREAD | UNRESOLVED`) — условие в `WHERE` каждой ветки (`UNRESOLVED` отключает
ветку бесед через `AND :filter <> 'UNRESOLVED'`). Нормализация `limit.coerceIn` — в routes.

Выход — полиморфная схема `InboxItemSchema` (sealed, дискриминатор `type`):
`ClientConversationItemSchema(clientId, clientName, lastMessageBody, lastMessageAt,
lastMessageDirection, unreadCount)` и `UnresolvedChatItemSchema(...)` с полями
`TriageChatSchema`. Так фронт разбирает записи исчерпывающим `switch`, а у беседы клиента
нет пустых полей чата.

*Альтернатива:* два запроса (беседы и очередь) и слияние на фронте. Отклонено: сломается общая
сортировка и пагинация, а `CLAUDE.md` требует сортировку и пагинацию в SQL.

`/messaging/triage/list` и `InboundTriageView` после перехода фронта не нужны: маршрут,
проекция, схема `TriageListResponse` и их тест удаляются в этом же изменении. `triage/chat`
и `triage/assign` остаются.

Сортировка бесед идёт по `conversations.last_message_at`, поэтому `Conversation.touch()`
теперь ставит его по самому позднему сообщению беседы, а не `now()`: иначе чат, привязанный
вручную спустя время, всплывал бы наверх со временем привязки, а не последнего сообщения.

### 3. Отметка прочтения: `POST /messaging/conversation/read` с `upTo`

`MarkConversationReadRequest(clientId, upTo: String /* ISO-8601 */)`. Фронт передаёт время
последнего сообщения, которое он показал. Сервер: проверяет, что клиент есть в организации
(иначе `CLIENT_NOT_FOUND`), берёт беседу без создания (если беседы нет — ничего не делает) и
выполняет `markRead` с `GREATEST(last_read_at, :upTo)`, чтобы горизонт только рос.
Нужен метод «найти беседу без создания» в `Conversations` (`byClient(): Conversation?`)
и правка `DbConversationReadStates.markRead` на `GREATEST`.

Это команда, поэтому живёт в `usecases/messaging` (`markConversationRead`), а не в `read/`.

*Альтернатива:* отмечать прочтение внутри `GET /messaging/conversation`. Отклонено: GET с
побочным эффектом ломает CQS, и periodic refetch на фоне прочитывал бы беседу на неактивной вкладке.
*Альтернатива:* `upTo = now()` на сервере. Отклонено: сообщение, пришедшее между загрузкой
ленты и отметкой, станет прочитанным, хотя его никто не видел.

Фронт шлёт отметку, когда беседа открыта, вкладка видима (`document.visibilityState`) и
`upTo` больше последнего отправленного значения.

### 4. Счётчик в меню — отдельный лёгкий запрос

`GET /messaging/inbox/counter` → `InboxCounterResponse(count: Int)`, метод `counter()` в той же
`InboxView`: `count` бесед с непрочитанным входящим плюс `count(DISTINCT (integration_id,
from_address))` неразобранных. Фронт опрашивает его раз в 30 с (как `NotificationBell`)
и инвалидирует после отметки прочтения и привязки.

### 5. Опрос вместо push

TanStack Query `refetchInterval`: список «Входящих» и открытая беседа — 15 с, счётчик — 30 с;
`refetchIntervalInBackground: false`. Это укладывается в требование «не больше 30 с»
и не требует новой инфраструктуры. WebSocket — в планах (`CLAUDE.md`), интерфейс не мешает.

### 6. Web: общий компонент беседы

Из панели чата карточки выделяется `ConversationThread({ api, clientId, onGuardChange })` — лента,
`ChannelPicker`, `AddressPicker`, ввод и отправка, отметка прочтения. `ConversationSheet`
становится обёрткой: передаёт черновик в `EditSheet` через `onGuardChange`, чтобы закрытие
с неотправленным текстом спрашивало подтверждение. «Входящие» рендерят тот же компонент
в правой панели плюс шапку со ссылкой на карточку.

Выбранная запись хранится в search-параметрах `/inbox` (`?client=<id>` или
`?chat=<integrationId>:<address>`, `filter=`), декодируется zod — ссылка на беседу
открывается напрямую и переживает перезагрузку. После `triage/assign` фронт инвалидирует
список и меняет search на `?client=<id>` — так чат «превращается» в беседу клиента.

На ширине < `md` показывается либо список, либо беседа с кнопкой «назад».

## Risks / Trade-offs

- [`LATERAL` по сообщениям для каждой беседы организации] → внутренний `UNION ALL` отдаёт
  только ключ записи и `last_at` (для бесед это колонка `c.last_message_at`), к нему применяются
  фильтр, `ORDER BY` и `LIMIT`, а `LATERAL` за последним сообщением и счётчиком непрочитанных —
  во внешнем запросе только для строк страницы. Индекс `conversations (org_id, last_message_at DESC)`.
- [Фильтр «Непрочитанные» требует непрочитанные до `LIMIT`] → для этой ветки `LATERAL`/
  `EXISTS` по входящим после горизонта неизбежен до пагинации; индекс
  `(conversation_id, created_at)` его покрывает. Объёмы студии (сотни бесед) это переносят.
- [Общая прочитанность: один сотрудник открыл — у других пропал бейдж] → осознанный выбор
  для общего ящика; переход на per-employee — новая таблица с `employee_id` без изменения API.
- [Пустые беседы от ленивого `forClient`] → отсекаются по `last_message_at IS NOT NULL`.
- [Опрос раз в 15 с от каждой открытой вкладки] → `refetchIntervalInBackground: false`;
  запросы лёгкие и скоупятся индексами.

## Migration Plan

Liquibase-миграция с индексом на `conversations`; данные не мигрируют. У всех бесед
горизонта нет, значит, после выката все входящие в существующих беседах будут непрочитанными.
Чтобы не завалить счётчик историей, та же миграция проставляет
`conversation_read_state.last_read_at = now()` для всех существующих бесед с сообщениями.
Откат — обычный откат релиза: таблицы и старые маршруты не меняются.
