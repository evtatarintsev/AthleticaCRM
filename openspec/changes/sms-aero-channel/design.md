## Context

Мотивация — proposal.md, требования — `specs/sms-aero-channel/spec.md` и дельта
`specs/channel-settings/spec.md`.

Что есть:
- `ChannelConfig` в `shared` — `sealed interface`, подтип и есть вид канала; хранится в БД
  как JSONB с дискриминатором `type`, ограничений на значения `type` в схеме БД нет — миграция
  не нужна.
- `ChannelTransports` / `ChannelTransport.send(address, body): SendResult` — порт отправки;
  `ProviderChannelTransports` — единственное место, где перечислены провайдеры.
  `SendResult` различает `Sent(providerRef)`, `TransientFailure` и `PermanentFailure`.
- `ChannelConfigVerifier` — проверка конфига при сохранении; для Telegram-бота вызывает `getMe`
  и маппит ответ в `TELEGRAM_TOKEN_INVALID` / `TELEGRAM_UNAVAILABLE`. Для SMS-провайдеров
  возвращает конфиг как есть.
- Каждый вид канала проходит по одному набору мест: схемы `NewChannelConfig`,
  `ChannelConfigChange`, `ChannelSettingsSchema`, `ChannelProviderSchema` и их регистрация в
  `AppJson`; ветки в `ChannelsRoutes`, `UpdateChannel`, `DbChannelListView`,
  `DbClientChannelsView`; форма в `web/src/settings/channels/`. Образец — `SmscSms`.
- HTTP-клиенты интеграций: `TelegramBotApi` (результат `TelegramResult`: `Ok` / `Error` /
  `Unavailable`), `YookassaApi` (Basic-авторизация, `requestTimeoutMillis`).

API SMS Aero v2: `https://gate.smsaero.ru/v2`, HTTP Basic (логин — email аккаунта, пароль —
API-ключ), JSON `{success, data, message}`. Нужны три метода: `/auth`, `/sign/list`
(страницы по 50, у подписи `name` и `status`: 1 — активна, 2 — отклонена; иначе — на
модерации), `/sms/send` (`number`, `text`, `sign`; в ответе `data.id`).

## Goals / Non-Goals

**Goals:**
- Один клиент SMS Aero для проверки и отправки; транспорт не знает, кто и зачем отправляет —
  будущие изменения (коды, приглашения, уведомления) используют его через `ChannelTransports`.

**Non-Goals:**
- Квитанции о доставке. У SMS Aero есть `callbackUrl`, но в сервере нет перехода доставки
  в `DELIVERED` ни для одного провайдера; механизм квитанций — общий, отдельным изменением.
- Тестовые методы SMS Aero (`/sms/testsend`) и режим «без реальной отправки» для development.
- Длинные тексты: SMS Aero сам делит текст на части, стоимость — забота организации.

## Decisions

### 1. Вид канала `SmsAeroSms(email, apiKey, sign)`, `type = "sms_aero_sms"`

`supportsInbound = false`, `supportsReceipts = false`, `addressing = ByContact(PHONE)`.
`supportsReceipts = false` — честный ответ, пока квитанций нет; включится вместе с ними.

Схемы ответа: `ChannelSettingsSchema.SmsAeroSms(email, sign, apiKeyMask)` — маска по тому же
правилу, что токен бота. Изменение: `ChannelConfigChange.SmsAeroSms(email, sign, apiKey?)` —
пустой ключ сохраняет текущий (как пароль SMSC).

- *Альтернатива — общий вид «SMS» с полем провайдера:* ломает принцип «подтип = вид канала» и
  требует перестройки уже работающих SMSC/Twilio.

### 2. `SmsAeroApi` в `infra/messaging/smsaero/`

Ktor-клиент, заголовок `Authorization: Basic base64(email:apiKey)` на каждый запрос (ключи
у каждой организации свои, поэтому передаются в метод, а не в конструктор), таймаут
запроса 10 секунд. Базовый URL — параметр конструктора с значением по умолчанию, чтобы тесты
шли на заглушку HTTP-сервера; в `application.conf` не выносится.

Результат — `SmsAeroResult<T>`: `Ok(value)`, `Error(status, message)` (ответ SMS Aero с HTTP
4xx или `success: false`), `Unavailable` (нет соединения, HTTP 5xx), `NoResponse` (запрос ушёл,
ответа нет — таймаут; SMS Aero мог его выполнить). Форма — как у
`TelegramResult`, чтобы верификатор и транспорт маппили исходы одинаково.

Методы: `auth(credentials)`, `sign(credentials, name)` — перебирает страницы `/sign/list`,
пока не найдёт подпись с точным совпадением `name` или страницы не кончатся;
`send(credentials, number, text, sign)` — `id` сообщения.

### 3. Проверка при сохранении — в `ProviderChannelConfigVerifier`

Порядок: обрезать пробелы → `auth` → `sign`.

| Ответ | Ошибка |
|---|---|
| `auth` → `Error` с 401/403 | `SMS_AERO_AUTH_INVALID` |
| подпись не найдена | `SMS_AERO_SIGN_NOT_FOUND` |
| `status` подписи не 1 | `SMS_AERO_SIGN_NOT_APPROVED` (с именем подписи) |
| `Unavailable` или иной `Error` | `SMS_AERO_UNAVAILABLE` |

Повторная проверка при изменении — по существующему правилу `UpdateChannel`: только если поле
провайдера отличается от сохранённого.

- *Альтернатива — проверять только ключ:* канал с неодобренной подписью сохранился бы и падал
  на каждой отправке; ошибку лучше показать в форме, где её можно исправить.

### 4. Маппинг исходов отправки в `SendResult`

| Исход `send` | `SendResult` |
|---|---|
| `Ok(id)` | `Sent(id)` |
| `Error` 400 (ошибка запроса, в т.ч. неверный номер) | `PermanentFailure("SMS_AERO_REJECTED", message)` |
| `Error` 402 (недостаточно средств) | `PermanentFailure("SMS_AERO_NO_FUNDS", …)` |
| `Error` 404 (IP не в белом списке) | `PermanentFailure("SMS_AERO_IP_FORBIDDEN", …)` |
| `Error` 401/403 (ключ отозван после сохранения) | `PermanentFailure("SMS_AERO_AUTH_INVALID", …)` |
| `Unavailable` | `TransientFailure("SMS_AERO_UNAVAILABLE", …)` |
| `NoResponse` (таймаут) | `PermanentFailure("SMS_AERO_NO_RESPONSE", …)` — SMS могло уйти, повтор отправил бы платный дубль |

402 — окончательный отказ для этого сообщения: повтор через минуту не поможет, а
автоматический повтор после пополнения отправил бы устаревший текст. Тексты причин — из
`i18n`, по ним администратор понимает, что делать в кабинете SMS Aero.

Адрес — номер из контакта клиента в нормализованном виде (`PhoneDigits`, без `+`), как
ожидает SMS Aero.

### 5. Журналы

Логгер транспорта и клиента пишет код ошибки, `message` от SMS Aero и `…NNNN` (последние 4
цифры номера). Email аккаунта пишется (он не секрет и нужен для разбора), API-ключ и текст —
нет. Заголовок авторизации не логируется: логирование HTTP-клиента Ktor не включается.

## Risks / Trade-offs

- [Модерация сообщений SMS Aero до 5–10 минут без договора] → для чата терпимо; для кодов
  подтверждения это критично — учитывать в изменении, которое будет отправлять коды.
- [Бесплатная подпись «SMS Aero» только для тестов, операторы блокируют коды без указания
  источника] → верификатор пропускает её, если она активна в аккаунте; ответственность за
  подпись — на организации, подсказка в форме ведёт на регистрацию подписи.
- [Подпись отклонили после сохранения канала] → отправка получит `Error` 400 →
  `PermanentFailure` с причиной от SMS Aero; повторная проверка при следующем изменении канала.
- [Белый список IP включён в кабинете организации] → ошибка 404 с понятной причиной; IP
  сервера Athletica документируется в подсказке формы, если понадобится.
- [Формат кода ошибки неверных учётных данных (401 или `success: false`) не подтверждён
  документацией] → проверить на реальном аккаунте (задача 4.2) и поправить маппинг до релиза.
