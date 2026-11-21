# Tasks

## Принятое решение по ревью

Дополнительную блокировку `users` для сериализации входа и сброса не добавляем. Риск сессии от входа,
проверившего старый пароль до сброса, принят явно (см. design.md). Ограничение повторных попыток входа
через fail2ban отнесено к отдельной инфраструктурной задаче и здесь не считается реализованным.

## 1. Схема БД, ссылка и письмо

- [x] 1.1 Добавить `0088-password-resets.sql`: таблица `password_resets` (см. design.md: `user_id` PK с FK на `users` `ON DELETE CASCADE`, `token_hash` UNIQUE, `expires_at`, `last_sent_at`, `used_at`), rollback (`TestPostgres.truncate` очистит таблицу через `TRUNCATE users CASCADE`). Проверка: `./gradlew test --tests "*EmailVerificationsTest"` поднимает схему без ошибок
- [x] 1.2 `EmailVerificationConfig.passwordResetLink(token)` → `<CRM_PUBLIC_URL>/reset-password#token=...`, с той же проверкой формата токена, что у `confirmationLink`. Проверка: новый кейс в `EmailVerificationConfigTest`
- [x] 1.3 `passwordResetEmail(to, name, lang, link)` в `SystemEmailTemplate.kt` и строки RU/EN в `Messages` (тема, текст, срок 1 час, кнопка, подвал «если это не вы — проигнорируйте»). Проверка: тест шаблона — обе версии на нужном языке, неподдерживаемый язык даёт русский, текстовая версия содержит полную ссылку и срок, имя с HTML экранируется

## 2. Подтверждение email по факту владения адресом

- [x] 2.1 `EmailVerifications.confirmOwnership(userId, clientIp)`: `FOR UPDATE` по `user_id`, ничего не делает без ожидающего подтверждения или при `email != login`; общий с `confirm` приватный метод пишет дату, уведомление `EmailConfirmed` и audit `AUTH_VERIFY_EMAIL`. KDoc на русском. Проверка: кейсы в `EmailVerificationsTest` — неподтверждённый email подтверждается с одним уведомлением и одним событием audit; повторный вызов и вызов без строки ничего не меняют; существующие тесты `confirm` проходят

## 3. Компонент `PasswordResets`

- [x] 3.1 `AuditActionType.AUTH_PASSWORD_RESET("auth_password_reset")` с локализацией RU/EN в `Messages` (рядом с `AUTH_CHANGE_PASSWORD`). Проверка: `./gradlew compileKotlin`
- [x] 3.2 `domain/auth/PasswordResets.kt`: `request(email, lang)` — один `INSERT ... ON CONFLICT ... WHERE last_sent_at <= now - 60s RETURNING`, письмо ставится только при возвращённой строке, в контексте `SystemContexts.forOrg(orgId)`; `reset(token, newPassword, lang, clientIp)` — проверка формата токена и непустого пароля, `UPDATE ... RETURNING` с фильтром `blocked_at IS NULL`, затем новый хэш, `sessions.deleteOthers(userId, null)`, `emailVerifications.confirmOwnership`, audit; одна ошибка `INVALID_PASSWORD_RESET` с локализованным сообщением без токена. Время из `Clock`. KDoc на русском
- [x] 3.3 DB-тест `PasswordResetsTest`: письмо для действующего сотрудника; нет письма для неизвестного и заблокированного адреса; второй запрос в пределах 60 с не создаёт письмо и не меняет хэш, спустя 60 с — гасит прежнюю ссылку; успешный сброс меняет хэш, удаляет все сессии пользователя и пишет одно событие audit; повтор, просроченная (час, через `Clock`), заменённая, неизвестная ссылка и ссылка после блокировки дают `INVALID_PASSWORD_RESET` без изменений; пустой пароль отклоняется, ссылка остаётся рабочей; сброс подтверждает ожидающий email. Проверка: `./gradlew test --tests "*PasswordResetsTest"`
- [x] 3.4 Зарегистрировать `PasswordResets` в `Di.kt`. Проверка: `./gradlew compileKotlin`

## 4. Маршруты и контракты

- [x] 4.1 Схемы `ForgotPasswordRequest(email)` и `ResetPasswordRequest(token, newPassword)` в `shared` `api/schemas/auth` с KDoc. Проверка: `./gradlew compileKotlin`
- [x] 4.2 В `authRoutes` добавить `POST /auth/forgot-password` (всегда пустой `200`) и `POST /auth/reset-password` (cookie не трогает: в браузере может быть открыт другой аккаунт) с `.contract(postContract<...>())`, язык — `langFromRequest`, IP — `clientIp()`. Проверка: route-тест рядом с `EmailConfirmationRoutesTest` — ответы для существующего и неизвестного адреса совпадают по статусу и телу; после сброса прежний access-токен получает 401, вход со старым паролем отклоняется, с новым проходит
- [x] 4.3 Перегенерировать контракты. Проверка: `cd web && npm run contracts`, `web/src/api/generated/` обновлён и закоммичен

## 5. Веб-клиент

- [x] 5.1 Строки RU/EN в `src/i18n/ru.ts` / `en.ts`: ссылка «Забыли пароль?», заголовки и подсказки обеих страниц, нейтральное сообщение после запроса, успех сброса, недействительная ссылка, несовпадение паролей. Проверка: `npm run check` (typecheck ловит пропущенный ключ)
- [x] 5.2 `auth/ForgotPasswordPage.tsx` на `AuthLayout`: поле email, кнопка, после `ok` — нейтральное сообщение в `role="status"`, ошибка сети — через `apiErrorMessage`. Ссылка «Забыли пароль?» в `LoginPage`. Проверка: `ForgotPasswordPage.test.tsx` — сообщение после отправки, ссылка со страницы входа ведёт на `/forgot-password`
- [x] 5.3 `auth/ResetPasswordPage.tsx`: новый пароль и подтверждение (zod `refine` на совпадение), явная кнопка; `INVALID_PASSWORD_RESET` — сообщение и ссылка на `/forgot-password`; успех — сообщение и ссылка на `/login`. Проверка: `ResetPasswordPage.test.tsx` — несовпадение не шлёт запрос, успех показывает переход ко входу, недействительная ссылка показывает переход к новому запросу
- [x] 5.4 Роуты `/forgot-password` и `/reset-password` в `app/router.tsx` (токен из `#token=` с очисткой фрагмента, как у `confirmEmailRoute`), оба пути в `staticAppPaths`. Проверка: `npm run format && npm run check`

## 6. Проверка целиком

- [x] 6.1 `./gradlew build` и `./gradlew ktlintFormat` без ошибок и изменений после форматирования
- [ ] 6.2 В запущенном приложении пройти восстановление: «Забыли пароль?» → письмо в очереди (`org_emails`) → ссылка → новый пароль → вход с новым паролем; вторая вкладка с прежней сессией получает выход на страницу входа. Только клавиатурой
