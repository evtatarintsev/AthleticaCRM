# Design

## Context

Мотивация — в proposal.md. Что есть сейчас:

- `JwtConfig` подписывает access- (15 мин) и refresh-токен (30 дней) без серверного состояния.
  Refresh-токен несёт `userId` и `branchId`. Оба токена лежат в HttpOnly-cookie, а refresh-cookie
  ограничена путём `/api/auth/refresh-token`.
- `contextFromRequest` уже открывает транзакцию на каждый защищённый запрос: загружает
  сотрудника, проверяет `blocked_at` (иначе `EmployeeBlocked` → HTTP 401) и отмечает присутствие.
  Ещё одна проверка по первичному ключу в той же транзакции почти ничего не стоит.
- `/auth/refresh-token` пересобирает пользователя через `Auth.userById` (фильтр `blocked_at IS NULL`)
  и выпускает новую пару токенов. `switch-branch` тоже выпускает новую пару.
- `Auth.logout()` пустой: маршрут только чистит cookie.
- Блокировка — `DbEmployee.block()`. Ограничения «не владелец, не себя» проверяются там же,
  веб дублирует их флагом `canBlock`.
- `employees` уникален по `(user_id, org_id)`, а `userById` уже считает, что у пользователя
  один сотрудник.

## Goals / Non-Goals

**Goals:**

- Отзыв сессии действует со следующего запроса — и для access-, и для refresh-токена.
- Инвариант «у заблокированного сотрудника нет сессий» держит агрегат сотрудника, а не маршрут.
- Список сессий — обычная read-проекция по правилам слоя `read/`.

**Non-Goals:**

- Гео по IP, переименование устройств, фоновая чистка таблицы.
- Кэширование проверки сессии: запрос уже ходит в БД за сотрудником.
- Отзыв по организации или массовый отзыв из админского CLI.

## Decisions

### Сессия — строка `auth_sessions`, отзыв — `DELETE`

```
auth_sessions
  id               UUID PK          -- sid, AuthSessionId (uuid v7)
  user_id          UUID NOT NULL    -- FK users ON DELETE CASCADE
  org_id           UUID NOT NULL    -- скоуп проекций
  created_at       TIMESTAMPTZ NOT NULL
  last_seen_at     TIMESTAMPTZ NOT NULL
  user_agent       TEXT NULL
  ip               TEXT NULL
  refresh_jti      UUID NOT NULL
  prev_refresh_jti UUID NULL
  rotated_at       TIMESTAMPTZ NOT NULL
  INDEX (user_id)
```

Колонка `revoked_at` отвергнута: завершённую сессию никто не читает, а сам факт блокировки
уже пишется в журнал действий. Удаление строки проще и не оставляет «мёртвых» сессий в выборках.

### Токены: `sid` в обоих, `jti` в refresh

`makeAccessToken(user, sid)` добавляет claim `sid`. `makeRefreshToken(userId, branchId, sid, jti)`
добавляет `sid` и стандартный `jti`. Токен без `sid` при разборе считается недействительным, поэтому
отдельная проверка версии формата не нужна. Отсюда и BREAKING из proposal.

### Агрегат `AuthSessions` в `domain/auth`

Интерфейс с командами, реализация `DbAuthSessions`:

- `new(userId, orgId, userAgent, ip): IssuedSession(sid, jti)` — заодно удаляет просроченные сессии
  этого пользователя (`last_seen_at` старше TTL refresh-токена). Так таблица не растёт без джоба.
- `rotate(sid, presentedJti, ip, now): jti` — `SELECT … FOR UPDATE` по `sid`:
  - `presentedJti == refresh_jti` → новый `jti`, `prev = старый`, `rotated_at = now`,
    `last_seen_at = now`, `ip`;
  - `presentedJti == prev_refresh_jti` и `now - rotated_at ≤ 60 с` → то же самое (окно гонки);
  - иначе → `DELETE` сессии и `raise(SessionRevoked)`; при отсутствии строки — тоже `SessionRevoked`.
- `reissue(sid): jti` — для `switch-branch`: ротация без предъявленного токена, сессия та же.
- `requireActive(sid, userId)` — проверка для `contextFromRequest`.
- `delete(sid, userId)`, `deleteOthers(userId, keep: sid?)` — при `keep = null` удаляются все сессии пользователя.

Окно 60 с отвергнуто как «ненужное» не было: без него две вкладки (cookie общая) или повтор
запроса после обрыва связи убивают сессию. Альтернатива — блокировка обновления между вкладками
через Web Locks — закрывает только вкладки, но не повтор после обрыва, и добавляет код на клиенте.

### Проверка сессии в `contextFromRequest`

В существующей транзакции рядом с `blocked_at`: `sessions.requireActive(sid, userId)`.
Отказ в `/auth/refresh-token` — `CommonDomainError("SESSION_REVOKED")` (HTTP 400, как прочие
отказы обновления). `rotate` при повторном предъявлении токена удаляет сессию и возвращает `null`,
не поднимая ошибку: иначе откат транзакции вернул бы удалённую сессию.
`SessionRevoked` — `AppError`, который `StatusPages` отдаёт как HTTP 401 так же, как
`EmployeeBlocked`. Веб уже обрабатывает 401: пробует обновиться, получает отказ и уходит
на страницу входа. Клиентских изменений для этого не нужно.

`EmployeeRequestContext` получает поле `sessionId: AuthSessionId?` (`null` только у служебного
контекста проверки пароля при входе, когда сессии ещё нет). Оно нужно для отметки текущей
сессии в списке, `deleteOthers` и `logout`.

### Блокировка — внутри `DbEmployee.block()`

`DbEmployees` получает `AuthSessions` через конструктор и передаёт его в `DbEmployee`. `block()`
после проверок и `UPDATE blocked_at` вызывает `sessions.deleteOthers(userId, keep = null)`, если `userId` задан.
Так инвариант держится при любом пути блокировки, а не только в маршруте.

Завершение одной сессии сотрудника — новая команда агрегата `Employee.terminateSession(sid)`
с теми же `ensure`, что у `block()` (не владелец, не себя), и `sessions.delete(sid, userId)`.
Если сессия не принадлежит этому пользователю, `delete` ничего не находит и поднимает
`AUTH_SESSION_NOT_FOUND`.

### Смена пароля и выход

`DbAuth` получает `AuthSessions`: `changePassword` после `UPDATE` вызывает
`sessions.deleteOthers(ctx.userId, ctx.sessionId)`, а `logout` — `sessions.delete(ctx.sessionId, ctx.userId)`.

### Маршруты (без path-переменных)

| Маршрут | Тело / query | Что делает |
|---|---|---|
| `GET /auth/sessions` | — | свои сессии |
| `POST /auth/sessions/terminate` | `{ sessionId }` | завершить свою, кроме текущей (`CANNOT_TERMINATE_CURRENT_SESSION`) |
| `POST /auth/sessions/terminate-others` | — | все свои, кроме текущей |
| `GET /employees/sessions` | `?employeeId=` | сессии сотрудника |
| `POST /employees/sessions/terminate` | `{ employeeId, sessionId }` | `employee.terminateSession(sid)` |

`login`, `sign-up`, `refresh-token`, `switch-branch` продолжают отвечать `LoginResponse`.
Меняется только содержимое токенов. `User-Agent` и IP для `new` и `rotate` берутся из запроса
(`clientIp()` уже есть).

### Проекция `read/auth/SessionListView`

`SessionListQuery(target: Me(userId) | Employee(employeeId), currentSessionId, now, maxIdle)`.
Один SQL. Для `Employee` — `JOIN employees e ON e.user_id = s.user_id WHERE e.id = :employeeId
AND e.org_id = :orgId`. Если сотрудник не найден в организации, поднимается `EMPLOYEE_NOT_FOUND`.
Фильтр `last_seen_at > now - maxIdle`, порядок `last_seen_at DESC`. `now` и `maxIdle` (TTL refresh)
задаёт маршрут.

Ответ — `SessionListResponse(items: List<SessionItem>)`:
`SessionItem(id, browser: String?, os: String?, platform: DevicePlatform, ip: String?,
createdAt, lastSeenAt, current: Boolean)`.
`DevicePlatform` — `MACOS | WINDOWS | LINUX | IOS | ANDROID | OTHER`, по нему выбирается иконка.
Право завершать сессию сотрудника веб выводит так же, как `canBlock`. Отдельное поле в ответе
не нужно: правило одно и уже живёт в карточке.

### Разбор User-Agent — свой, в Kotlin

Небольшая чистая функция `userAgentInfo(ua): UserAgentInfo(browser, os, platform)` рядом
с проекцией. Порядок проверок важен: Edge/Opera/Yandex до Chrome, Chrome до Safari, iOS
(iPhone/iPad) до macOS, Android до Linux. Версия браузера — только мажорная.

Библиотека ua-parser отвергнута: новая зависимость с базой регулярок ради строки «Chrome · macOS»
не окупается. Ошибка разбора стоит только неточной подписи («Неизвестное устройство»).

Разбор идёт при чтении, хранится сырой `user_agent`: поправка парсера сразу исправляет
старые строки.

### Web

- `web/src/account/SessionsSheet.tsx` — вложенная панель из `ProfileSheet`, тем же приёмом,
  что `ChangePasswordSheet`. «Завершить все другие» — через существующий диалог подтверждения.
- `web/src/account/SessionRow.tsx` — строка сеанса (иконка по `platform`, «browser · os»,
  IP · время). Переиспользуется в `EmployeeCardSheet`.
- Раздел в `EmployeeCardSheet`: запрос `employees/sessions`; после `block` / `unblock` и после
  завершения инвалидируется вместе с карточкой.
- Строки — в `ru.ts` / `en.ts`. Контракты генерируются `npm run contracts`.

### Связь user ↔ employee 1:1

Миграция: `DROP INDEX employees_user_id_org_id_key; CREATE UNIQUE INDEX employees_user_id_key
ON employees (user_id) WHERE user_id IS NOT NULL`. Кода, который привязывает существующего
пользователя ко второму сотруднику, нет (`invite` всегда создаёт нового `users.new`), поэтому
`onViolation` не нужен: нарушение — программная ошибка (HTTP 500).

## Risks / Trade-offs

- [Лишний запрос на каждый защищённый запрос] → поиск по первичному ключу в уже открытой
  транзакции. Проверить `ContextFromRequestBenchmark` до и после.
- [Ложное «украденный токен» при медленной сети дольше 60 с] → пользователь просто входит заново,
  данные не теряются. Окно — константа, его легко увеличить.
- [Самописный разбор User-Agent ошибается на редких браузерах] → показывается «Неизвестное
  устройство» или неточное имя. Сессия при этом остаётся видимой и завершаемой.
- [Все разлогинятся при выкате] → прода нет; на стендах это ожидаемо.
- [Таблица растёт за счёт брошенных сессий] → они удаляются при следующем входе того же
  пользователя. Сессии пользователей, которые больше не входят, остаются. Объём ничтожен,
  джоб можно добавить позже без изменения спеков.

## Migration Plan

1. Liquibase `0087-auth-sessions.sql`: таблица `auth_sessions`, замена уникального индекса
   `employees`. Rollback: `DROP TABLE`, вернуть индекс `(user_id, org_id)`.
2. Выкат сервера и веба одним релизом. Старые токены без `sid` отклоняются, пользователи
   входят заново.
3. Откат: предыдущая версия игнорирует `sid` и `jti`, а токены по подписи остаются валидными.
   Откат миграции не обязателен.
