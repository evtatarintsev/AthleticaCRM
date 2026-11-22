## Context

Мотивация — proposal.md, требования — `specs/client-app-push/spec.md`. Порт
`ClientAppPushes.enqueue(accessId, title, body)` и заглушка — design `client-mobile-app` §1;
устройства `client_app_devices` и подтверждения `client_app_device_verifications` — design
ядра §3. Вызывающий — `ClientAppTransport` из `client-app-chat`.

Фоновая доставка с повторами уже есть у сообщений каналов (`MessageDispatcher`,
`deliveries`) — тот же подход здесь.

## Goals / Non-Goals

**Goals:**
- Постановка в очередь в транзакции вызывающего (outbox): уведомление не теряется при
  откате и не уходит о несохранённом сообщении.
- Адресаты вычисляются в момент отправки, а не постановки.

**Non-Goals:**
- Тихие push, бейджи с числом непрочитанных, действия в уведомлении.
- Аналитика доставки и открытий.

## Decisions

### 1. Таблицы

| Таблица | Суть |
|---|---|
| `client_app_push_tokens` | `device_id` (PK, FK на `client_app_devices` `ON DELETE CASCADE`), платформа, токен, `enabled`, `updated_at` |
| `client_app_push_outbox` | `id`, `access_id`, заголовок, текст, `created_at`, `attempts`, `next_attempt_at`, `done_at` |
| `client_app_push_sent` | (`outbox_id`, `device_id`) — защита от повторной доставки на устройство при повторе |

Каскад от устройства: выход, завершение сеанса и удаление аккаунта в ядре удаляют токены без
кода в этом изменении. Номер миграции больше номера миграции ядра (design ядра §3).

### 2. Адресаты — в момент отправки

Обработчик берёт запись очереди и одним SQL выбирает токены: устройства с подтверждением
этого доступа, доступ `подключён`, приложение в организации включено, клиент не в архиве,
`enabled = true`, нет строки в `client_app_push_sent`. Так блокировка, отключение и выход,
случившиеся после постановки, сразу действуют.

### 3. FCM HTTP v1 для Android, APNs напрямую для iOS

- FCM: `POST https://fcm.googleapis.com/v1/projects/{project}/messages:send`, OAuth2-токен из
  ключа сервисного аккаунта (кэш на время жизни токена). `UNREGISTERED`/`INVALID_ARGUMENT` по
  токену → удалить токен; 429/5xx → повтор.
- APNs: HTTP/2, JWT провайдера из ключа `.p8` (обновление раз в 50 минут),
  `apns-topic = bundle id`, sandbox или production по конфигурации. `410 Unregistered` /
  `BadDeviceToken` → удалить токен; 429/5xx → повтор.
- *Альтернатива — FCM и для iOS (Firebase iOS SDK пересылает в APNs):* один адаптер на
  сервере, но Firebase SDK в iOS-сборке Compose Multiplatform и зависимость доставки на iOS от
  Google. Прямой APNs на сервере — один небольшой HTTP/2-клиент.

### 4. Повторы

Интервал `min(2^attempts минут, 1 час)`, запись очереди отбрасывается через 24 часа после
`created_at`. Запись закрыта (`done_at`), когда по всем адресатам доставка успешна или токен
удалён.

### 5. Маршруты

`POST /api/client-app/push/token` — регистрация или замена токена текущего устройства
(платформа + токен). `POST /api/client-app/push/settings` — `enabled` для текущего устройства.
Оба под `client-jwt`, работают в `ClientAccountContext` (уведомления принадлежат устройству,
а не профилю).

### 6. Конфигурация

```
push {
    fcm.serviceAccountJson = ${?PUSH_FCM_SERVICE_ACCOUNT_JSON}
    apns.keyP8 = ${?PUSH_APNS_KEY_P8}
    apns.keyId = ${?PUSH_APNS_KEY_ID}
    apns.teamId = ${?PUSH_APNS_TEAM_ID}
    apns.bundleId = ${?PUSH_APNS_BUNDLE_ID}
    apns.sandbox = true
    apns.sandbox = ${?PUSH_APNS_SANDBOX}
}
```

Без ключей провайдера уведомления этой платформы остаются в очереди и отбрасываются по
истечении 24 часов; в журнал — предупреждение при старте.

## Risks / Trade-offs

- [Текст уведомления виден на заблокированном экране] → только название организации и начало
  сообщения, без имени клиента (спецификация).
- [Очередь растёт без ключей провайдера] → отбрасывание через 24 часа, удаление закрытых записей
  старше 7 дней тем же обработчиком.
- [Учётной записи Apple ещё нет] → APNs проверяется в sandbox с ключом разработчика; production
  — после [#274](https://github.com/evtatarintsev/AthleticaCRM/issues/274).
