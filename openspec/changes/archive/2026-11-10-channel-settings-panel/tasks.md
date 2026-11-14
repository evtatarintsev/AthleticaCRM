## 1. Контракты (shared)

- [x] 1.1 В `api/schemas/channels` добавить `ChannelSettingsSchema` (ответ с масками), `NewChannelConfig` (создание) и `ChannelConfigChange` (изменение, секреты `String?`) с дискриминатором `type` и прежними `@SerialName`; `ChannelShareSchema(url)`; `SetChannelEnabledRequest(id, enabled)` (design п. 1, 5). Перевести `ChannelIntegrationSchema` на `settings: ChannelSettingsSchema` и `share: ChannelShareSchema?` вместо `config`/`botLink`. `CreateChannelIntegrationRequest.config` перевести на `NewChannelConfig`, `UpdateChannelIntegrationRequest` — на `name` и `config: ChannelConfigChange` без `enabled`. Проверка: `./gradlew :shared:compileKotlinJvm`; в `shared` не осталось API-схем, ссылающихся на `core.messaging.ChannelConfig`.

## 2. Сервер

- [x] 2.1 Команда `ChannelIntegrations.setEnabled(id, enabled)` в `DbChannelIntegrations` (один `UPDATE` со скоупом по `org_id`, `CHANNEL_NOT_FOUND`) и в `AuditChannelIntegrations` (аудит без секретов). Проверка: доменный DB-тест — выключение, повторное включение, чужая организация даёт `CHANNEL_NOT_FOUND`.
- [x] 2.2 Слияние `ChannelConfig` с `ChannelConfigChange` и признак «учётные данные изменились» (design п. 3): `null`-секрет берётся из сохранённого, другой `type` — `CHANNEL_PROVIDER_MISMATCH`, у Telegram без нового токена сохраняется `botUsername`. Проверка: юнит-тест на каждого провайдера — секрет не передан, секрет равен сохранённому, секрет новый, изменено несекретное поле, несовпадение провайдера.
- [x] 2.3 Usecase `usecases/channels/UpdateChannel`: чтение в транзакции, проверка через `ChannelConfigVerifier` вне транзакции и только при изменении учётных данных, запись с текущим `enabled`. Проверка: usecase-тест с фейковым верификатором — переименование верификатор не вызывает; новый токен вызывает его и сохраняет `botUsername` из ответа; отказ верификатора не меняет канал.
- [x] 2.4 `ChannelsRoutes`: `create` переводит `NewChannelConfig` в доменный конфиг и проверяет его как раньше; `update` вызывает `UpdateChannel`; добавить `post /channels/set-enabled`. Проверка: `./gradlew compileKotlin`, тест маршрута `set-enabled` (или существующий тест маршрутов каналов) зелёный.
- [x] 2.5 `DbChannelListView` собирает `ChannelSettingsSchema` с масками (`••••` + 4 последних символа при длине от 12, иначе `••••`) и `share` для Telegram с непустым `botUsername` независимо от `enabled` (design п. 2, 5). Проверка: DB-тест в `read/channels/` — в сериализованном ответе нет значений токена, пароля и auth token; маски длинного и короткого секрета; `share` у включённого и выключенного бота, у бота без имени и у SMSC его нет.
- [x] 2.6 Перегенерировать контракты: `cd web && npm run contracts`. Проверка: в `web/src/api/generated/contracts.ts` есть `channels/set-enabled`, а в схеме ответа списка нет полей `botToken`/`password`/`authToken`.

## 3. Web: основа

- [x] 3.1 Добавить `@radix-ui/react-switch` и `components/ui/switch.tsx` в стиле существующих shadcn-компонентов; добавить `react-qr-code` (design п. 7, 8). Проверка: `npm install` проходит, `npm run check` зелёный.
- [x] 3.2 Строки i18n в `ru.ts`/`en.ts`: шаг выбора провайдера, «Назад», подсказка «оставьте пустым, чтобы не менять» с маской, подпись переключателя, «Поделиться», «Скачать PNG», «Поделиться…» (текст ошибки `CHANNEL_PROVIDER_MISMATCH` локализует сервер в `Messages`). Удалить строки, которые больше не используются (`channels.field.provider`, `channels.field.enabled`, `channels.disabled` при отсутствии использований). Проверка: `npm run check` (typecheck словарей).

## 4. Web: панель канала

- [x] 4.1 Формы провайдеров `SmscChannelForm`, `TelegramChannelForm`, `TwilioChannelForm`, каждая со своей zod-схемой: при создании секрет обязателен; при изменении поле пустое и необязательное, маска в `placeholder` и подсказке, пустое значение уходит как `null` (design п. 6). Проверка: тесты форм — при изменении без ввода секрета в запросе нет секрета; маска не попадает в `value`.
- [x] 4.2 `ChannelSheet` на `EditSheet` с шагами «провайдер → форма» для создания, «Назад» с подтверждением при несохранённых данных и сразу формой для изменения; удалить `ChannelDialog`. Проверка: тест — создание Telegram-бота через выбор провайдера отправляет `channels/create`; «Назад» с заполненной формой спрашивает подтверждение; изменение открывается без шага выбора.

## 5. Web: список и «Поделиться»

- [x] 5.1 `ChannelShareButton({ name, share })`: диалог со ссылкой и копированием, QR-код, «Скачать PNG» (SVG → canvas → PNG), «Поделиться…» только при наличии `navigator.share`, `AbortError` не считается ошибкой (design п. 8). Проверка: тест — ссылка и QR показываются, копирование пишет ссылку в буфер, кнопки системного меню нет без `navigator.share`.
- [x] 5.2 `ChannelsPage`: в строке переключатель с оптимистичным `channels/set-enabled`, откатом и `toast.error` при ошибке; «Поделиться» при наличии `share`; изменение и удаление открывают `ChannelSheet` и `ConfirmDialog`. Проверка: обновлённый `ChannelsPage.test.tsx` — переключение отправляет `set-enabled`, при ошибке переключатель возвращается; «Поделиться» есть у выключенного бота и отсутствует у SMSC.

## 6. Проверка

- [x] 6.1 Полный прогон: `./gradlew build`, `./gradlew ktlintFormat`, в `web/` — `npm run format` и `npm run check`; закоммитить `web/src/api/generated/`. Вручную в браузере: создать Telegram-бота через панель, переключить его, открыть «Поделиться», скачать PNG и проверить, что в ответе `channels/list` во вкладке Network нет токена.
