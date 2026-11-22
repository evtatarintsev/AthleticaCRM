## 1. Клиент SMS Aero

- [x] 1.1 `SmsAeroApi` и `SmsAeroResult` в `infra/messaging/smsaero/`: Basic-авторизация email + API-ключ на каждый запрос, таймаут 10 с, базовый URL параметром конструктора; методы `auth`, `sign` (перебор страниц `/sign/list` до точного совпадения), `send`; тест на заглушке HTTP-сервера: заголовок авторизации, параметры `/sms/send`, разбор `success: true/false`, вторая страница подписей, HTTP 5xx и таймаут → `Unavailable`

## 2. Вид канала и проверка

- [x] 2.1 `ChannelConfig.SmsAeroSms` (`sms_aero_sms`) и варианты `NewChannelConfig`, `ChannelConfigChange`, `ChannelSettingsSchema` (маска ключа), `ChannelProviderSchema` с регистрацией в `AppJson`; ветки в `ChannelsRoutes`, `UpdateChannel` (пустой ключ сохраняет текущий), `DbChannelListView`, `DbClientChannelsView`; тесты: в списке каналов маска и нет ключа, изменение без ключа не меняет его, смена провайдера → `CHANNEL_PROVIDER_MISMATCH`
- [x] 2.2 Ветка SMS Aero в `ProviderChannelConfigVerifier` и тексты ошибок в `i18n` (`ru`/`en`): `SMS_AERO_AUTH_INVALID`, `SMS_AERO_SIGN_NOT_FOUND`, `SMS_AERO_SIGN_NOT_APPROVED`, `SMS_AERO_UNAVAILABLE`; тест верификатора на заглушке HTTP-сервера для каждой ошибки и успеха с обрезкой пробелов; тест `UpdateChannel`: переименование не обращается к SMS Aero
- [x] 2.3 `SmsAeroSmsTransport` и ветка в `ProviderChannelTransports`: маппинг исходов в `SendResult` по таблице design §4, причины из `i18n`, журнал без ключа и текста, номер — последние 4 цифры; тест на заглушке HTTP-сервера для `Sent`, 400, 402, 404, 401 и `Unavailable` и проверка текста журнала

## 3. Веб-клиент

- [x] 3.1 Перегенерировать контракты (`cd web && npm run contracts`) и закоммитить `web/src/api/generated/`
- [x] 3.2 Форма `SmsAeroChannelForm` (название, email, API-ключ, подпись; ключ в изменении пустой с маской в подсказке; подсказка про регистрацию подписи), SMS Aero в списке провайдеров `ChannelSheet`, строки `ru`/`en`; тесты в `ChannelsPage.test.tsx`: создание канала SMS Aero, изменение без ключа отправляет запрос без `apiKey`, ошибка проверки оставляет панель открытой с данными; `npm run format` и `npm run check` проходят

## 4. Проверка интеграции

- [x] 4.1 `./gradlew build` и `./gradlew ktlintFormat` проходят
- [ ] 4.2 На реальном аккаунте SMS Aero: сохранить канал с неверным ключом, с подписью на модерации и с одобренной подписью — ошибки и успех совпадают с design §3; отправить сообщение из чата клиента и получить SMS; при расхождении кодов ответа SMS Aero поправить маппинг и тесты
