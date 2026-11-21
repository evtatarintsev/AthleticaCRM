# Design

## Context

Мотивация — в proposal.md, требования — в `specs/password-reset/spec.md`. Что есть сейчас:

- `EmailVerifications` (`domain/auth`) — образец механизма: 32 байта `SecureRandom` в base64url,
  в БД только SHA-256, письмо через `OrgEmails.schedule` в контексте `SystemContexts.forOrg`,
  ссылка из `EmailVerificationConfig` (`CRM_PUBLIC_URL`, токен во фрагменте `#token=`).
- `AuthSessions.deleteOthers(userId, keep = null)` удаляет все сессии пользователя; токены без
  живой сессии отклоняются со следующего запроса.
- После `0087-auth-sessions` у пользователя ровно один сотрудник, поэтому организация письма
  и события audit — организация этого сотрудника.
- `verifyCredentials` не пускает сотрудника с `blocked_at IS NOT NULL`.
- На сервере требований к паролю нет ни при регистрации, ни в `changePassword`. Веб проверяет
  только непустое значение (`z.string().min(1)`).
- Публичные маршруты (`/auth/confirm-email`, `/auth/login`) живут в `authRoutes` на `Route`, без `RouteWithContext`.

## Goals / Non-Goals

**Goals:**

- Каждый шаг (запрос, сброс) — одна транзакция; гонки параллельных запросов письма и использования одного токена решает сам SQL-запрос, без блокировок в Kotlin.
- Время ответа на запрос сброса почти не зависит от того, существует ли адрес.

**Non-Goals:**

- Общая таблица одноразовых токенов для всех назначений (подтверждение, сброс, будущее приглашение).
- Политика сложности пароля.

## Decisions

### Отдельная таблица `password_resets`, одна строка на пользователя

```
password_resets
  user_id      UUID PK  REFERENCES users ON DELETE CASCADE
  token_hash   TEXT     NOT NULL UNIQUE
  expires_at   TIMESTAMPTZ NOT NULL
  last_sent_at TIMESTAMPTZ NOT NULL
  used_at      TIMESTAMPTZ NULL
```

Ключ по `user_id` даёт «новая ссылка гасит прежнюю» без отдельного шага: прежний хэш перезаписывается.
`org_id` не храним: его даёт единственный сотрудник пользователя.

Альтернатива — общая `user_tokens(purpose, ...)` вместе с `user_email_verifications`. Отклонена:
у подтверждения своя история (`verified_at`, повторная отправка из профиля), а объединять имеет смысл,
когда появится третье назначение (ссылка «задайте пароль» в приглашении).

### Запрос: один `INSERT ... ON CONFLICT` решает и паузу, и гонку

```
INSERT INTO password_resets (user_id, token_hash, expires_at, last_sent_at)
SELECT u.id, :hash, :now + 1h, :now
FROM users u JOIN employees e ON e.user_id = u.id
WHERE u.login = :email AND e.blocked_at IS NULL
ON CONFLICT (user_id) DO UPDATE
  SET token_hash = EXCLUDED.token_hash, expires_at = EXCLUDED.expires_at,
      last_sent_at = EXCLUDED.last_sent_at, used_at = NULL
  WHERE password_resets.last_sent_at <= :now - 60s
RETURNING user_id, <org_id и имя сотрудника через подзапрос>
```

Нет строки в ответе — значит, адрес неизвестен, сотрудник заблокирован или идёт пауза. Письмо
не ставится, маршрут отвечает тем же пустым `200`. Параллельные запросы сериализует сама строка
при `ON CONFLICT`. Это проще, чем `SELECT ... FOR UPDATE` + ветвление как в `EmailVerifications.resend`,
и работает для ещё не существующей строки, которую `FOR UPDATE` заблокировать не может.

Токен генерируется до запроса всегда, даже если письма не будет, чтобы обе ветки выполняли одну и ту же работу.

### Сброс: один `UPDATE ... RETURNING` расходует ссылку

```
UPDATE password_resets r SET used_at = :now
FROM employees e
WHERE r.token_hash = :hash AND r.used_at IS NULL AND r.expires_at > :now
  AND e.user_id = r.user_id AND e.blocked_at IS NULL
RETURNING r.user_id, e.org_id, e.id, e.name
```

Нет строки — `INVALID_PASSWORD_RESET` (одна ошибка для всех причин, по спецификации). Второй
параллельный запрос ждёт блокировку строки и после фиксации первого видит `used_at IS NOT NULL`.
Затем в той же транзакции: `UPDATE users SET password_hash`, `sessions.deleteOthers(userId, null)`,
подтверждение email, `audit.log(AUTH_PASSWORD_RESET)`. Ошибка любого шага откатывает и `used_at`.

Формат токена проверяется до SQL (`[A-Za-z0-9_-]{43}`), как в `confirm`. Пустой `newPassword` → `CommonDomainError`
до SQL. Требования к паролю не появляются ни при регистрации, ни в `changePassword`:
это отдельная задача, и эта задача её не начинает.

### Принятый риск: вход одновременно со сбросом

По итогам ревью 2026-11-21 явно отказались от дополнительной блокировки `users` (`FOR UPDATE OF u`
или `FOR SHARE OF u`) при проверке credentials. Не добавляем синхронизацию входа и сброса и ожидание
блокировки на пути аутентификации ради редкого пересечения этих операций.

Принимаем сценарий: вход проверил старый пароль, затем сброс обновил хэш и удалил сессии,
после чего начатый ранее вход создал новую действующую сессию. Отзыв сессий при сбросе не гарантирует
завершения такого входа. Новый запрос входа после фиксации сброса уже не принимает старый пароль.

Многократные попытки входа, в том числе попытки попасть в это окно, планируем ограничивать через fail2ban.
Настройка fail2ban — отдельная инфраструктурная задача, в этом изменении она не реализована.
Fail2ban ограничивает повторные попытки, но саму гонку и возможность единичного совпадения не устраняет.

### Компонент `PasswordResets` рядом с `EmailVerifications`

Конкретный класс в `domain/auth` с зависимостями `OrgEmails`, `AuthSessions`, `EmailVerifications`,
`AuditLog`, `PasswordHasher`, `EmailVerificationConfig`, `Clock`. Методы `request(email, lang)` и
`reset(token, newPassword, lang, clientIp)`. Тот же уровень, что `EmailVerifications`: сущность
поверх таблиц `auth`, без отдельного интерфейса — у него одна реализация и тестируется он на реальной БД.

Альтернатива — методы `Auth.forgotPassword/resetPassword` в `DbAuth`. Отклонена: `DbAuth` и так
собирает много зависимостей, а сброс не пользуется ни одной из его внутренностей, кроме `passwordHasher`.

### Подтверждение email из сброса — метод `EmailVerifications`

`EmailVerifications.confirmOwnership(userId, clientIp)`: `SELECT ... WHERE v.user_id = :userId FOR UPDATE OF v`.
Если строки нет, `verified_at` уже стоит или `email != login`, метод ничего не делает. Иначе тот же код,
что в `confirm` после проверки токена: дата, уведомление `EmailConfirmed`, audit `AUTH_VERIFY_EMAIL`.
Общая часть выносится в приватный метод, чтобы правила подтверждения жили в одном месте.

### Ссылка и письмо

- `EmailVerificationConfig.passwordResetLink(token)` → `<CRM_PUBLIC_URL>/reset-password#token=...`.
  Класс не переименовываем, хотя он теперь шире: переименование — шум в диффе ради имени.
- `passwordResetEmail(to, name, lang, link)` в `SystemEmailTemplate.kt` по образцу `registrationEmail`;
  новые строки в `Messages` (тема, текст, срок «1 час», кнопка, подвал «вы или кто-то запросили сброс; если не вы — проигнорируйте»).

### Маршруты и схемы

- `POST /auth/forgot-password` `ForgotPasswordRequest(email: String)` → `Unit`.
- `POST /auth/reset-password` `ResetPasswordRequest(token: String, newPassword: String)` → `Unit`.
  Cookie ответ не трогает: сессии пользователя удалены, и его токены получат 401 сами,
  а открытую в этом браузере сессию другого аккаунта сброс завершать не должен.
- Оба — в `authRoutes`, рядом с `/auth/confirm-email`, с `.contract(postContract<...>())`.

### Веб

- `/forgot-password`: форма email на `AuthLayout`; после ответа (любого `ok`) — нейтральное сообщение.
- `/reset-password`: токен из `#token=` с очисткой фрагмента, как в `confirmEmailRoute`; поля «новый пароль»
  и «подтверждение» (zod, `refine` на совпадение); при `INVALID_PASSWORD_RESET` — сообщение и ссылка на `/forgot-password`;
  при успехе — сообщение и ссылка на `/login`.
- `LoginPage` — ссылка «Забыли пароль?», оба пути в `staticAppPaths`.

## Risks / Trade-offs

- [Время ответа выдаёт существование адреса: при известном адресе добавляется запись письма в очередь] →
  Это одна вставка в той же транзакции. Разница — миллисекунды и тонет в сетевом шуме. Отправка SMTP асинхронна.
- [Без ограничения по IP можно засыпать письмами много разных адресов] → Каждому адресу не чаще
  одного письма в минуту. Ограничение по IP — вне объёма, его можно добавить позже без изменения спецификации.
- [Злоумышленник с доступом к почте сбрасывает пароль и выкидывает владельца из всех сессий] → Это
  неизбежное свойство восстановления по email. Событие audit фиксирует сброс с IP.
- [`EmailVerificationConfig` отвечает уже не только за подтверждение] → Принимаем. Переименуем, когда появится третья ссылка.

## Migration Plan

Новая миграция `0088-password-resets.sql` создаёт таблицу (rollback — `DROP TABLE`). Прода нет,
перенос данных не нужен. Переменные окружения не добавляются.
