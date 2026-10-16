## Context

Мотивация — в `proposal.md`, требования — в `specs/telegram-bot` и `specs/inbound-triage`.

Что есть на master:

- `ChannelConfig.TelegramBot(botToken)` с адресацией `ByBinding`. `TelegramBotTransport` — заглушка, её отдаёт `StubChannelTransports`.
- `channel_bindings (integration_id, client_id, address)` с `UNIQUE (integration_id, address)`. `link()` делает upsert, то есть молча перепривязывает адрес. `addressFor()` берёт самую старую связку клиента.
- `inbound_receipts` — сырые входящие (`UNRESOLVED`/`RESOLVED`). `receiveInbound`/`assignInbound` разбирают по одному входящему. Триаж (`InboundTriageView`, `/messaging/triage/list|assign`) работает по отдельным записям, UI в `web/` нет.
- `MessageDispatcher` — outbox исходящих: временные и окончательные сбои через `SendResult`.
- `Conversations`, `Conversation`, `ChannelBindings`, `InboundReceipts` и `receiveInbound` требуют `EmployeeRequestContext`, хотя используют только `orgId`. Системного пути для входящих нет.
- Телефоны в `client_contacts.value` хранятся как ввёл пользователь, без нормализации.
- HTTP-клиент: Ktor client на движке CIO (YooKassa). CIO не умеет SOCKS-прокси.
- Фоновые циклы запускаются в `Application.kt` корутинами в `CoroutineScope(SupervisorJob)`.

## Goals / Non-Goals

**Goals:**
- Каждое обновление Telegram обрабатывается ровно один раз при рестартах и нескольких инстансах, без session-level блокировок через R2DBC-пул.
- Приём, автопривязка и ручной разбор — общий код для всех связанных каналов. Telegram-специфика остаётся в `infra.messaging.telegram`.
- Домен не знает о прокси и Bot API.

**Non-Goals:**
- Общий фреймворк опроса для будущих каналов (VK и т.п.). Пока поллер у Telegram свой.
- Нормализация телефонов при вводе в карточке клиента. Сравнение нормализует обе стороны на лету.
- Метрики и алерты по здоровью прокси.

## Decisions

### 1. Аренда опроса в таблице вместо advisory-lock

Состояние опроса хранится в новой таблице `telegram_poll_state (integration_id PK, update_offset BIGINT, lease_owner TEXT, lease_until TIMESTAMPTZ)`. Поллер захватывает аренду так: `UPDATE … SET lease_owner = :me, lease_until = now() + 60s WHERE integration_id = :id AND (lease_until < now() OR lease_owner = :me)`. Аренда продлевается каждым циклом. `lease_owner` — случайный id инстанса, генерируется при старте.

Почему не `pg_try_advisory_lock`: блокировка уровня сессии привязана к соединению. Через R2DBC-пул её пришлось бы держать на выделенном соединении всё время работы поллера и следить за его обрывом. Аренда в таблице переживает обрыв сама: истёк `lease_until` — бота подхватывает другой инстанс.

### 2. Ровно один раз: offset и входящие в одной транзакции

Цикл: `getUpdates(offset, timeout = 25s)` вне транзакции. Затем одна транзакция: проверка, что аренда ещё наша (`lease_owner = :me`), запись входящих, обновлений чатов и связок, `update_offset = max(update_id) + 1`. Если транзакция откатилась, offset не сдвинулся, и Telegram отдаст те же обновления снова. Если аренду перехватили, транзакция ничего не пишет, и поллер останавливается. Ответы бота клиенту (приветствие, подтверждение) отправляются после коммита. Это «не более одного раза», и для служебных реплик это приемлемо.

### 3. Супервизор поллеров

`TelegramPollingSupervisor` каждые 10 секунд читает включённые Telegram-интеграции (системный запрос без org-скоупа) и сверяет их с картой запущенных корутин. Новые интеграции запускает, удалённые, отключённые и со сменившимся токеном — отменяет. Поэтому новая или включённая интеграция начинает опрашиваться без рестарта. Запускается в `Application.kt` рядом с `messageDispatcher`.

Альтернатива — сигнал из use case создания интеграции. Отклонена: на другом инстансе этот сигнал не сработает, а периодическая сверка проще и самовосстанавливается.

### 4. Прокси: список URL, OkHttp, перебор

Конфигурация — `telegram.proxies` в `application.conf`, строка URL через запятую из env `TELEGRAM_PROXIES`, например `socks5://user:pass@host:1080,http://host2:3128`. Одна строка вместо HOCON-списка объектов, потому что так проще передать её через docker-compose. Парсится при старте в `TelegramProxy(type, host, port, credentials?)`. Некорректная запись — ошибка старта. Пустая строка — прямой доступ.

`TelegramBotApi` держит по `HttpClient(OkHttp)` на каждый прокси. HTTP-прокси задаётся через `ProxyBuilder.http`, SOCKS5 — через `ProxyBuilder.socks`. Аутентификацию задаём через OkHttp `proxyAuthenticator` для HTTP и `java.net.Authenticator` для SOCKS. Запрос идёт через «последний удачный» прокси. При сетевой ошибке или таймауте соединения он повторяется через следующие по кругу. Ответ Bot API с ошибкой (`ok = false`) — это ответ, а не сбой прокси, перебор на нём не происходит. Таймаут запроса — long-poll плюс 10 секунд.

Почему OkHttp: из движков Ktor только он поддерживает SOCKS. CIO остаётся для YooKassa.

### 5. Проверка токена при сохранении — порт в домене

В `domain/channels` появляется интерфейс `ChannelConfigVerifier { verified(config): ChannelConfig }` с `Raise<DomainError>`. Реализация в infra: для `TelegramBot` она вызывает `getMe` и возвращает конфиг с `botUsername`. Ошибки — `TELEGRAM_TOKEN_INVALID` (401/404 от Bot API) и `TELEGRAM_UNAVAILABLE` (все прокси недоступны). Остальные конфиги возвращаются как есть. Проверку вызывают маршруты `/channels/create|update` до `channels.add/update`. Внешний вызов делается до открытия транзакции.

`ChannelConfig.TelegramBot` получает поле `botUsername: String = ""`. Клиент его не задаёт: значение из запроса перезаписывается результатом `getMe`. `ChannelListView` отдаёт ссылку `botLink` для Telegram-интеграций.

Уникальность токена обеспечивает частичный уникальный индекс `channel_integrations ((config->>'botToken')) WHERE config->>'type' = 'telegram_bot'`. Нарушение индекса при `add/update` переводится в `TELEGRAM_BOT_IN_USE`: перед вставкой — явный `SELECT EXISTS`, индекс страхует гонку.

### 6. Чаты связанных каналов: таблица `channel_chats`

Новая таблица `channel_chats (id, org_id, integration_id, address, display_name, username, phone, created_at, UNIQUE (integration_id, address))` хранит сведения о собеседнике в канале. Это техническая информация: в контакты клиента она не попадает и в карточке не редактируется. Чат создаётся или обновляется при каждом апдейте. `phone` записывается только из контакта, у которого `contact.user_id == from.id`.

Таблица общая для связанных каналов, а не `telegram_chats`. Триаж и привязка работают по паре `(integration_id, address)` и о Telegram не знают.

### 7. Разбор по чату

- Связка: `channel_bindings.link()` больше не делает upsert. Вставка идёт с `ON CONFLICT (integration_id, address) DO NOTHING`. Если строка не вставилась — `CHAT_ALREADY_LINKED`. Так две одновременные привязки разрешает уникальный индекс.
- `assignChat(integrationId, address, clientId)`: проверить, что клиент и чат существуют в организации (иначе `NOT_FOUND`), затем `link`. После этого перенести все `UNRESOLVED` входящие этого адреса в беседу клиента в порядке `received_at` и пометить их разобранными.
- `InboundTriageView` группирует `inbound_receipts WHERE state = 'UNRESOLVED'` по `(integration_id, from_address)` и соединяет с `channel_chats` и `channel_integrations` (имя канала). Сортировка — по `max(received_at) DESC`. Отдельный эндпоинт `GET /messaging/triage/chat` отдаёт сообщения одного чата для правой панели.
- `channel_bindings` получает колонку `last_inbound_at`. `addressFor()` сортирует по ней (`DESC NULLS LAST`, затем `created_at`), так ответ уходит в последний активный чат.

### 8. Системный контекст для приёма

Методы `ChannelBindings`, `InboundReceipts`, `Conversations`/`Conversation` (`appendInbound`, `touch`, `forClient`), `ClientContacts.byPhone` и use case `receiveInbound` переводятся на `context(ctx: RequestContext, …)`. Это не ослабление прав: они используют только `ctx.orgId`. Методы, которым нужен сотрудник (`appendOutbound` с автором, `assignChat`), остаются на `EmployeeRequestContext`. Поллер собирает контекст через `SystemContexts.forOrg(orgId интеграции)`.

### 9. Автопривязка по номеру

Номер нормализуется: остаются только цифры; 11 цифр с ведущей `8` превращаются в ведущую `7`. Сравнение идёт с тем же выражением над `client_contacts.value` в SQL (`regexp_replace` + `CASE`) в пределах `org_id` и `type = 'PHONE'`, с `DISTINCT client_id`. Ровно один клиент — `link` и перенос накопленных входящих, как при ручном разборе. Иначе номер остаётся в `channel_chats.phone` для триажа. Полный проход по телефонам организации на каждый присланный контакт допустим: событие редкое. Индекс по выражению — когда понадобится.

### 10. Обработка апдейта в Telegram-слое

Маппинг `Update → InboundEvent` живёт в `infra.messaging.telegram` и покрывается юнит-тестами без сети:

- Обрабатываются только `message` из `chat.type == private`. Остальные типы обновлений и чатов пропускаются, offset при этом всё равно сдвигается.
- `/start` — только служебная реплика, во входящие не пишется.
- `contact` — попытка автопривязки, во входящие не пишется.
- Текст — `text`. Вложение — `[вложение: <вид>]` + ` ` + `caption`. Виды: фото, видео, голосовое, аудио, документ, стикер, геопозиция, прочее. Строки берутся из i18n по языку организации.
- Служебные реплики бота (приветствие с `reply_markup.keyboard[[{request_contact}]]`, подтверждение привязки) — тексты в i18n сервера. Приветствие отправляется, пока у чата нет ни телефона, ни связки.

### 11. Отправка и ошибки

`TelegramBotTransport.send` вызывает `sendMessage(chat_id, text)` и маппит результат:

| Ответ | Результат |
|---|---|
| `ok = true` | `Sent(message_id)` |
| 429, 5xx, все прокси недоступны, таймаут | `TransientFailure` |
| 403 (бот заблокирован, пользователь удалён) | `PermanentFailure("BOT_BLOCKED")` |
| 400 `chat not found` | `PermanentFailure("CHAT_NOT_FOUND")` |
| прочие 400, 401 | `PermanentFailure` с описанием Bot API |

`retry_after` из 429 пока не учитывается: диспетчер повторяет со своим интервалом. `StubChannelTransports` переименовывается в `ProviderChannelTransports`, и ветка Telegram получает реальный транспорт. SMS остаются заглушками.

## Risks / Trade-offs

- [Секреты прокси и токены в открытом виде (env, JSONB)] → как и для остальных провайдеров. Шифрование конфигов — уже существующий TODO в `0053`, вне изменения. Токен не попадает в аудит (`AuditChannelIntegrations` уже исключает конфиг) и в логи: URL Bot API содержит токен, поэтому логируем только метод.
- [`channels/list` отдаёт токен бота любому сотруднику] → открытый вопрос из прошлого рефакторинга. Не ухудшаем его, но и не закрываем. Решение о маскировании — отдельно.
- [Долгая обработка пачки апдейтов дольше аренды] → пачка ограничена `limit = 50`, аренда 60 секунд, проверка аренды в транзакции не даёт двум инстансам записать одно и то же.
- [Клиент сменил номер или номер у семьи] → автопривязка только при однозначном совпадении, иначе разбор вручную. Ошибочную ручную привязку пока не исправить из UI: отвязка — следующий шаг, сейчас только через БД.
- [Bot API отвечает 409 Conflict, если для бота задан вебхук] → поллер при старте вызывает `deleteWebhook` (без удаления апдейтов).
- [Отказ одного прокси замедляет каждый запрос на таймаут соединения] → «липкий» последний удачный прокси. Таймаут соединения — 5 секунд.

## Migration Plan

1. Миграция `0079-telegram-polling.sql`:
   - `telegram_poll_state`;
   - `channel_chats`;
   - `channel_bindings.last_inbound_at`;
   - частичный уникальный индекс по токену.

   Имеющиеся Telegram-интеграции (заглушечные, без `botUsername`) продолжают читаться: у поля есть значение по умолчанию. Ссылка у таких интеграций появится после повторного сохранения.
2. Env `TELEGRAM_PROXIES` в `docker-compose.prod.yaml` и `.env.prod.example`. Пустое значение означает прямой доступ.
3. Откат — откат образа. Новые таблицы старый код не читает, `last_inbound_at` nullable.

## Open Questions

- Учитывать ли `retry_after` из 429 в диспетчере. Это можно сделать позже без изменения требований.
