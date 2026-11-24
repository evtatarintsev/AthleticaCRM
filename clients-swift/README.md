# clients-swift — эксперимент: клиентское API на Swift + Vapor

Сервис обслуживает `/api/clients/*` вместо Kotlin-сервера. Маршрутизацию делает nginx
(`nginx/prod.conf.template`): весь раздел уходит в Swift, кроме импорта.

| Эндпоинт                                                   | Где      |
| ---------------------------------------------------------- | -------- |
| `list`, `detail`, `create`, `edit`, `archive`, `restore`   | Swift    |
| `add-to-group`, `add-to-groups`, `remove-from-group`       | Swift    |
| `docs/attach`, `docs/delete`, `notes/*`                    | Swift    |
| `unpaid-sessions`, `export`                                | Swift    |
| `import/*`                                                 | Kotlin   |

Контракт (JSON запросов и ответов, коды ошибок) совпадает с Kotlin-сервером: веб-клиент не меняется.
Осознанное отличие — `export`: значения с запятой и кавычками берутся в кавычки (RFC 4180); Kotlin
этого не делает, и колонка «Баланс» (`1 200,50 ₽`) у него распадается на две.

## Фоновый воркер событий

Сервис пишет и обрабатывает доменные события через ту же outbox-таблицу `domain_events`, что и Kotlin:

- архивация клиента публикует `ClientArchived` в транзакции архивации;
- воркер (`Domain/Events/DomainEventWorker.swift`, порт `DomainEventWorker.kt`) выбирает пачки
  `FOR UPDATE SKIP LOCKED`, повторяет с задержкой 10 с · 2ⁿ (до часа), после 12 попыток ставит `failed_at`;
- обработчик `LeaveGroupsOnArchive` закрывает участия архивного клиента в группах. Решение — по текущему
  состоянию БД: повторная доставка и восстановление клиента до обработки не меняют итога.

Каждый воркер выбирает только типы событий своей модели (`event_type = ANY(...)`): Kotlin — подклассы
`DomainEvent`, Swift — типы с обработчиками на его шине. Воркер живёт в той же группе задач, что
HTTP-сервер и пул соединений, и останавливается вместе с ними.

## Как устроено

Принципы те же, что в Kotlin-коде (см. корневой `CLAUDE.md`), в переводе на Swift:

| Kotlin                                   | Swift                                                       |
| ---------------------------------------- | ----------------------------------------------------------- |
| `Raise<AppError>` / `Either`             | typed throws: `throws(AppError)`                            |
| context parameters `ctx`, `tr`           | явные параметры `ctx:`, `tr:`                               |
| `sealed interface Client`                | протоколы `ActiveClient` / `ArchivedClient` + `ClientInState` |
| декораторы `Audit*` через `by delegate`  | структуры-декораторы над протоколом                         |
| smart constructor (`NonBlankName`, …)    | приватный `init` + `static func from(_:) throws(AppError)`  |
| `domain/` и `read/`                      | `Domain/` (агрегаты) и `Read/` (проекции, один SQL)          |
| R2DBC + SQL                              | postgres-nio + SQL, без ORM; параметры только bind'ами      |

- Схему БД ведёт Kotlin-сервер (Liquibase); сервис только читает и пишет таблицы.
- Аутентификация — тот же JWT (`JWT_SECRET`), что выпускает Kotlin: cookie `access_token` или `Authorization: Bearer`.
  Заблокированный сотрудник и завершённая сессия — 401.

## Тесты

`swift test` — юнит-тесты и DB-тесты (порты Kotlin-тестов клиентов, заметок, контактов, списка, воркера).
DB-тесты поднимают `postgres:18` в Docker и применяют те же миграции Liquibase (`server/src/main/resources/db/changelog/changes/*.sql`
по порядку имён; директивы Liquibase в них — SQL-комментарии). Каждый тест заводит свою организацию,
поэтому тесты идут параллельно и не чистят таблицы; воркер событий тестируется последовательно.

## Запуск

```bash
swift build
swift test
POSTGRES_USER=… POSTGRES_PASSWORD=… JWT_SECRET=… swift run ClientsService
```

Переменные: `PORT` (8080), `POSTGRES_HOST` (localhost), `POSTGRES_PORT` (5432), `POSTGRES_DB` (athletica),
`POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`.

Полная проверка вместе с веб-клиентом — `mise run e2e`: стенд собирает и этот образ.
