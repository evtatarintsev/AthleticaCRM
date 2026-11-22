## Context

Мотивация — proposal.md. Порт, который реализуется, и политика выбора канала — design
`client-mobile-app` §1 и §6: `VerificationCodeSender` с `channel = TELEGRAM`, ошибки
`CodeDeliveryError.Undeliverable` (перейти на SMS) и `Failed`.

Что есть: `infra/messaging/telegram/` — клиент Bot API для бота организаций (`TelegramBotApi`)
и настройка прокси `telegram.proxies` в `application.conf` (Bot API из российских сетей
доступен не всегда). Gateway API — отдельный сервис (`gatewayapi.telegram.org`) со своим
токеном и методами; с Bot API общих только прокси.

## Goals / Non-Goals

**Goals:**
- Одна оплата за код: проверка доставляемости и отправка не должны оплачиваться дважды
  (условия тарификации сверить на тестовом токене, задача 2.3).

**Non-Goals:**
- Callback с отчётами о доставке (`callback_url`): требует публичного маршрута и проверки
  подписи, а позднюю недоставку уже закрывает повторный запрос по SMS.
- `checkVerificationStatus` — код хранит и проверяет ядро.

## Decisions

### 1. Сначала `checkSendAbility`, затем `sendVerificationMessage` с тем же `request_id`

`checkSendAbility(phone_number)` отвечает, можно ли доставить сообщение, и возвращает
`request_id`. Отрицательный ответ → `CodeDeliveryError.Undeliverable`. Положительный →
`sendVerificationMessage(phone_number, request_id, code, ttl = 300)`: по документации Gateway
API отправка с `request_id` из проверки не оплачивается повторно.

- *Альтернатива — сразу `sendVerificationMessage`:* на недоставляемый номер ошибку видно
  только по ответу отправки, и правило «сообщить до отправки» в спецификации было бы
  невыполнимо.

### 2. Код генерирует ядро

Передаём свой код в параметре `code`, а не просим Gateway сгенерировать. Иначе проверять
пришлось бы через Gateway, и лимиты попыток ядра расходились бы с лимитами Telegram.

### 3. Клиент — отдельный `TelegramGatewayApi` в `infra/telegram/gateway/`

Ktor HTTP-клиент, `Authorization: Bearer <token>`, тот же механизм прокси, что у Bot API
(список из `telegram.proxies`). Ответ `{ok: false, error}` и сетевые сбои → `Failed`, кроме
отрицательного ответа `checkSendAbility` → `Undeliverable`. Таймаут запроса — 5 секунд: ядро
ждёт ответа синхронно, пока человек смотрит на экран.

### 4. Конфигурация

```
telegramGateway {
    token = ${?TELEGRAM_GATEWAY_TOKEN}
}
```

Пустой токен в production — ошибка старта сервера (вход без Telegram-отправителя не должен
молча уходить в заглушку). В `development` без токена остаётся заглушка ядра.

## Risks / Trade-offs

- [Gateway API недоступен из сети сервера] → прокси `telegram.proxies`; при `Failed` ядро
  предлагает SMS.
- [Закончился баланс Gateway] → ответ с ошибкой → `Failed` → человек получает код по SMS;
  уведомление о балансе — в кабинете Gateway, мониторинг расхода — открытый вопрос.
- [Telegram принял сообщение, но человек его не видит] → кнопка «Отправить по SMS» в
  приложении (design ядра §6).

## Open Questions

- Нужен ли алерт на остаток баланса Gateway (`remaining_balance` есть в каждом ответе) — можно
  добавить после первых недель работы.
