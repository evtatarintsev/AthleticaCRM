## 1. Клиент Gateway API

- [ ] 1.1 Добавить `telegramGateway.token` в `application.conf` и `TELEGRAM_GATEWAY_TOKEN` в `docker-compose.prod.yaml` и `.env.prod.example` в том же коммите; проверить, что сервер не стартует в production без токена и стартует в `development`
- [ ] 1.2 `TelegramGatewayApi` в `infra/telegram/gateway/`: `checkSendAbility` и `sendVerificationMessage` (с `request_id`, своим `code`, `ttl = 300`), Bearer-токен, прокси из `telegram.proxies`, таймаут 5 с; тест на заглушке HTTP-сервера: заголовок авторизации, параметры запросов, разбор `ok: true` и `ok: false`

## 2. Отправитель кодов

- [ ] 2.1 `TelegramGatewayCodeSender : VerificationCodeSender` (`channel = TELEGRAM`): отрицательная проверка → `Undeliverable` без вызова отправки, ошибки и сбои сети → `Failed`; журнал без кода и ключа, номер — последние 4 цифры; тест на заглушке HTTP-сервера для всех трёх исходов и проверка текста журнала
- [ ] 2.2 Подключить отправитель в `Di.kt` вместо заглушки канала `TELEGRAM` при заданном токене; тест usecase запроса кода из ядра с этим отправителем на заглушке HTTP-сервера: недоставляемый номер → код ушёл по SMS-заглушке
- [ ] 2.3 Проверить отправку на реальный номер с тестовым токеном Gateway и что `request_cost` списан один раз за пару проверка + отправка; `./gradlew build` и `./gradlew ktlintFormat` проходят
