# clients-swift — эксперимент: клиентское API на Swift + Vapor

Сервис обслуживает часть `/api/clients/*` вместо Kotlin-сервера. Маршрутизацию делает nginx
(`nginx/prod.conf.template`): в Swift уходят только перенесённые эндпоинты, остальное — в `server`.

| Эндпоинт                                                   | Где      |
| ---------------------------------------------------------- | -------- |
| `list`, `detail`, `create`, `edit`, `archive`, `restore`   | Swift    |
| `add-to-group`, `add-to-groups`, `remove-from-group`       | Swift    |
| `docs/attach`, `docs/delete`                               | Swift    |
| `export`, `unpaid-sessions`, `notes/*`, `import/*`         | Kotlin   |

Контракт (JSON запросов и ответов, коды ошибок) совпадает с Kotlin-сервером: веб-клиент не меняется.

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

## Запуск

```bash
swift build
swift test
POSTGRES_USER=… POSTGRES_PASSWORD=… JWT_SECRET=… swift run ClientsService
```

Переменные: `PORT` (8080), `POSTGRES_HOST` (localhost), `POSTGRES_PORT` (5432), `POSTGRES_DB` (athletica),
`POSTGRES_USER`, `POSTGRES_PASSWORD`, `JWT_SECRET`.

Полная проверка вместе с веб-клиентом — `mise run e2e`: стенд собирает и этот образ.
