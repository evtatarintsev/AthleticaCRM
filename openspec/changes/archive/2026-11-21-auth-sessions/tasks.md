# Tasks

## 1. Схема БД и идентификатор сессии

- [x] 1.1 Добавить `0087-auth-sessions.sql`: таблица `auth_sessions` (см. design.md), индекс `(user_id)`, FK на `users` с `ON DELETE CASCADE`, замена `employees_user_id_org_id_key` на `UNIQUE (user_id) WHERE user_id IS NOT NULL`, rollback. Проверка: `./gradlew test --tests "*Migration*"` или любой DB-тест поднимает схему без ошибок
- [x] 1.2 Добавить `AuthSessionId` в `shared` `core/entityids` по образцу остальных ID. Проверка: `./gradlew compileKotlin`
- [x] 1.3 DB-тест: второй сотрудник с тем же `user_id` в другой организации отклоняется нарушением уникальности. Проверка: тест проходит

## 2. Агрегат `AuthSessions`

- [x] 2.1 `domain/auth/AuthSessions.kt` + `DbAuthSessions.kt`: `new` (с удалением просроченных сессий пользователя), `rotate` (FOR UPDATE, окно 60 с для `prev_refresh_jti`, иначе удаление и `SessionRevoked`), `reissue`, `requireActive`, `delete`, `deleteOthers`, `deleteAll`; `SessionRevoked : AppError` в `core/errors`. KDoc на русском
- [x] 2.2 Domain-тест `AuthSessionsTest`: ротация по текущему `jti`; приём прежнего `jti` в окне 60 с; удаление сессии при прежнем `jti` после окна и при неизвестном `jti`; `deleteOthers` оставляет текущую; `new` удаляет просроченные сессии этого пользователя и не трогает чужие. Проверка: `./gradlew test --tests "*AuthSessionsTest"`
- [x] 2.3 Зарегистрировать `DbAuthSessions` в `Di.kt`. Проверка: `./gradlew compileKotlin`

## 3. Токены и жизненный цикл сессии в маршрутах

- [x] 3.1 `JwtConfig`: claim `sid` в access и refresh, `jti` в refresh; сигнатуры `makeAccessToken(user, sid)` / `makeRefreshToken(userId, branchId, sid, jti)`. В `validate` плагина `auth-jwt` отклонять access-токен без `sid` (401, а не 500 от разбора claim). Проверка: `./gradlew compileKotlin`
- [x] 3.2 `EmployeeRequestContext.sessionId`; `contextFromRequest` берёт `sid` из токена и вызывает `sessions.requireActive` в той же транзакции; `StatusPages` отдаёт `SessionRevoked` как 401 наравне с `EmployeeBlocked`. Обновить `employeeContext()` в тестах (сессия по умолчанию). Проверка: `./gradlew compileTestKotlin`
- [x] 3.3 `AuthRoutes`: `login` и `sign-up` создают сессию (User-Agent, `clientIp()`); `refresh-token` читает `sid`/`jti` и вызывает `rotate`; `switch-branch` — `reissue` текущей сессии. `eitherToAuthResponse` принимает выпущенную сессию вместе с пользователем
- [x] 3.4 `DbAuth.logout` удаляет текущую сессию; `DbAuth.changePassword` вызывает `deleteOthers`. `AuthSessions` передаётся в `DbAuth` через конструктор
- [x] 3.5 Route-тесты (рядом с `JwtCookiesTest`): запрос с токеном удалённой сессии → 401; access-токен без `sid` → 401; refresh после logout отклоняется; switch-branch не создаёт новую сессию; смена пароля завершает другие сессии. Проверка: `./gradlew test --tests "org.athletica.crm.routes.*"`
- [x] 3.6 Прогнать `ContextFromRequestBenchmark` до и после 3.2 и записать результат в PR. Проверка: цифры в описании PR

## 4. Блокировка и завершение сессий сотрудника

- [x] 4.1 `DbEmployees`/`DbEmployee` получают `AuthSessions`; `block()` после `UPDATE` вызывает `deleteAll(userId)`, если логин выдан. Новая команда `Employee.terminateSession(sid)` с теми же `ensure`, что у `block()`; в `AuditEmployee` — запись в журнал
- [x] 4.2 Domain-тест: блокировка удаляет все сессии сотрудника, а после разблокировки старый refresh-токен не принимается; `terminateSession` для владельца и для себя отклоняется; чужая сессия → `AUTH_SESSION_NOT_FOUND`. Проверка: `./gradlew test --tests "*Employee*"`

## 5. Чтение и API сеансов

- [x] 5.1 `read/auth/userAgentInfo` — чистая функция разбора User-Agent в `browser`/`os`/`DevicePlatform`; unit-тест на Chrome/Edge/Firefox/Safari/YaBrowser для macOS/Windows/iOS/Android/Linux, пустую строку и `null`. Проверка: тест проходит
- [x] 5.2 Схемы в `shared` (`api/schemas/auth`): `SessionItem`, `SessionListResponse`, `DevicePlatform`, `TerminateSessionRequest`, `EmployeeSessionsRequest`, `TerminateEmployeeSessionRequest`. Проверка: `./gradlew compileKotlin`
- [x] 5.3 `read/auth/SessionListView` + `DbSessionListView` (один SQL, `Me`/`Employee`, фильтр `last_seen_at > now - maxIdle`, скоуп `org_id`), регистрация в `ReadViews`; DB-тест в `server/src/test/kotlin/org/athletica/crm/read/auth/`: отметка текущей сессии, скрытие давних, сотрудник чужой организации → `EMPLOYEE_NOT_FOUND`. Проверка: тест проходит
- [x] 5.4 Маршруты `GET /auth/sessions`, `POST /auth/sessions/terminate` (текущую — `CANNOT_TERMINATE_CURRENT_SESSION`), `POST /auth/sessions/terminate-others`, `GET /employees/sessions`, `POST /employees/sessions/terminate`; `now` и `maxIdle = jwtConfig.refreshTokenTtl` задаются в маршруте. Route-тест на завершение своей и текущей сессии. Проверка: тест проходит
- [x] 5.5 Перегенерировать контракты: `cd web && npm run contracts`. Проверка: `web/src/api/generated/contracts.ts` содержит новые маршруты

## 6. Web: сеансы в профиле и карточке сотрудника

- [x] 6.1 `web/src/account/SessionRow.tsx` (иконка по `platform`, «browser · os» или «Неизвестное устройство», IP · время) и `SessionsSheet.tsx`: разделы «Это устройство» и «Активные сеансы», крестик без подтверждения, «Завершить все другие сеансы» с подтверждением; вход из `ProfileSheet`. Строки в `ru.ts`/`en.ts`
- [x] 6.2 Тесты `account.test.tsx`: одна сессия — нет кнопки «Завершить все другие»; завершение убирает строку; отмена подтверждения ничего не меняет; ошибка сервера показывается в панели. Проверка: `npm run check`
- [x] 6.3 Раздел «Активные сеансы» в `EmployeeCardSheet` для сотрудника с выданным доступом; крестик только при `canBlock`; инвалидация `employees/sessions` после block/unblock и завершения
- [x] 6.4 Тест карточки: у владельца нет кнопок завершения; после блокировки список пустой. Проверка: `npm run check`

## 7. Итоговая проверка

- [x] 7.1 `./gradlew build`, `./gradlew ktlintFormat`, в `web/` — `npm run format` и `npm run check` проходят
- [x] 7.2 Вручную в браузере: вход в двух браузерах, завершение одного из другого (завершённый уходит на вход при следующем действии), «Завершить все другие», блокировка сотрудника выкидывает его. Проверка: скриншоты в PR
