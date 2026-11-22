## Context

Мотивация — proposal.md, требования — `specs/client-app-ui/spec.md`, `specs/client-app-android/spec.md`
и пользовательские сценарии ядра (`client-app-access`, `client-app-overview` в `client-mobile-app`,
`client-app-chat`, `client-app-push`). Макеты: https://claude.ai/artifact/USGK1UdgF7BxB3kEoDS8ox.

Что есть: `shared` — KMP-модуль только с `jvm()` (Kotlin 2.4, kotlinx-serialization, datetime,
Arrow); в `settings.gradle.kts` — `:server` и `:shared`, репозитории только `mavenCentral()`.
`Dockerfile.server` собирает `./gradlew :server:shadowJar`, CI `contracts.yml` запускает
`:server:generateWebContracts` — оба без Android SDK. Ktor-клиент уже в `libs.versions.toml`.

## Goals / Non-Goals

**Goals:**
- Весь UI и логика экранов — в `commonMain`; `androidMain` — только то, что требует Android API.
- iOS (`client-mobile-ios`) добавляет таргет без изменения общих экранов.

**Non-Goals:**
- Офлайн-кэш данных, WebSocket.
- Подписание релиза и выкладка в магазины — [#275](https://github.com/evtatarintsev/AthleticaCRM/issues/275).

## Decisions

### 1. Kotlin Multiplatform + Compose Multiplatform

Схемы запросов и ответов, `Money`, ID и правила вроде `ActiveStatus` из `shared` используются
напрямую — без генерации контрактов, как в `web/`.

- *Альтернатива — React Native / Expo:* команда пишет web на TS/React, но пришлось бы
  дублировать схемы через генерацию и терять общие доменные правила из `shared`.
- *Альтернатива — нативные Swift + Kotlin:* две кодовые базы на одну команду.
- Риск: Compose Multiplatform на iOS моложе, чем на Android — экраны простые (списки,
  карточки, формы); iOS-сборку проверяем рано в `client-mobile-ios`.

### 2. Мобильные таргеты — за свойством Gradle

Android-плагин требует Android SDK уже при конфигурации проекта. Поэтому:

- `settings.gradle.kts`: `include(":mobile")` и репозиторий `google()` — только если
  `providers.gradleProperty("athletica.mobile")` равно `true`;
- `shared/build.gradle.kts`: `androidTarget()` — под тем же условием (iOS-таргеты добавляет
  `client-mobile-ios`);
- свойство включают в `~/.gradle/gradle.properties` разработчика мобильного приложения и в
  мобильном CI (`-Pathletica.mobile=true`).

Сборка сервера, Docker и `contracts.yml` не меняются. Новый `mobile.yml` собирает debug APK и
гоняет тесты `:mobile` на Ubuntu-раннере с Android SDK.

### 3. Модуль `:mobile`

```
mobile/
  src/commonMain/   экраны, навигация, view-модели, сетевой слой, строки (compose resources)
  src/commonTest/   тесты view-моделей и сетевого слоя
  src/androidMain/  хранилище сеанса, FCM, разрешения, MainActivity
```

Android-приложение — `androidMain` того же модуля (одно приложение, один модуль), без
отдельного `androidApp`, пока ему нечего держать отдельно.

- Навигация — `navigation-compose` для Compose Multiplatform.
- Состояние экранов — view-модели (`lifecycle-viewmodel` Compose Multiplatform) с `StateFlow`.
- Строки — Compose Multiplatform resources, `ru` и `en`, как словари `web/`.

### 4. Сетевой слой

Ktor-клиент (`OkHttp` на Android; движок iOS добавит `client-mobile-ios`), `ContentNegotiation`
с `appJson` из `shared`, плагин `Auth` с bearer-токенами: при `401` — один запрос `refresh`,
при его неудаче — сброс сеанса и экран входа. Ошибки сервера — `ErrorResponse` из `shared`,
разбираются в значение, а не исключение (как `ApiResult` в `web/`).

До готовности маршрутов ядра экраны работают на `MockEngine` с ответами-примерами по схемам
`shared`; тот же `MockEngine` — в тестах.

### 5. Хранилище сеанса — `expect`/`actual`

`interface SessionStore { suspend fun load(): Session?; suspend fun save(s: Session); suspend fun clear() }`.
Android: DataStore с шифрованием ключом из Android Keystore (Tink), файл исключён из
резервного копирования (`dataExtractionRules` / `fullBackupContent`).
`EncryptedSharedPreferences` не берём: `androidx.security:security-crypto` объявлен устаревшим.

### 6. Push на Android

FCM SDK; токен отправляется на `/api/client-app/push/token` после входа и при обновлении токена
(`onNewToken`). Уведомление несёт `orgId` и `clientId` профиля в данных — по нажатию открывается
его переписка (требование «Переход из уведомления»). Разрешение `POST_NOTIFICATIONS` на Android
13+ — после первого подключения профиля.

### 7. Минимальная версия — Android 8.0 (API 26)

Покрывает практически все устройства в использовании и даёт `java.time` без desugaring.

## Risks / Trade-offs

- [Схемы `shared` меняются, пока экраны пишутся] → экраны на `MockEngine` по схемам; при изменении
  схемы компилятор показывает затронутые экраны.
- [Свойство `athletica.mobile` забыли включить] → `./gradlew build` проходит без мобильного модуля;
  проверку держит `mobile.yml` и мобильный раздел в `CLAUDE.md`.
- [Firebase недоступен у части пользователей] → переписка всё равно обновляется при открытии
  приложения; push — дополнение.
