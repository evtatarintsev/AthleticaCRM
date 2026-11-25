## Why

Клиентское API и веб-кабинет (`client-cabinet-web`) готовы, но клиенту нужно и мобильное
приложение. Начинаем с Android и общего для обеих платформ кода: на Kotlin Multiplatform +
Compose Multiplatform экраны пишутся один раз, а iOS (`client-mobile-ios`) добавляет к ним
платформенную оболочку.

## What Changes

- Новый Gradle-модуль `:mobile` (общий код + Android-приложение) на Kotlin Multiplatform и
  Compose Multiplatform; использует схемы и правила из `shared` напрямую, без генерации контрактов.
- Мобильные таргеты подключаются только при свойстве Gradle `athletica.mobile=true`: серверная
  Docker-сборка и CI по-прежнему без Android SDK. Отдельный CI-job для мобильной сборки.
- Общие экраны: вход по коду, принятие условий, подключение датами рождения, «Это не я»,
  переключатель профилей, главная, расписание с «Не придём», посещения, абонементы, баланс,
  переписка с вложениями, устройства и выход, удаление аккаунта.
- Android: хранение сеанса в защищённом хранилище, приём push через FCM, открытие переписки
  по нажатию на уведомление.
- **Не входит**: iOS (`client-mobile-ios`), публикация в Google Play
  ([#275](https://github.com/evtatarintsev/AthleticaCRM/issues/275)), оплата в приложении.

## Capabilities

### New Capabilities
- `client-app-ui`: поведение мобильного приложения, общее для Android и iOS — навигация,
  сеанс на устройстве, повтор кода, работа без сети, переход из уведомления, очистка при выходе.
- `client-app-android`: требования к Android-версии приложения — поддерживаемые версии,
  разрешение на уведомления, исключение сеанса из резервных копий.

### Modified Capabilities

## Impact

- **mobile** (новый модуль): `commonMain` (экраны, навигация, сетевой слой), `androidMain`,
  Android-приложение.
- **shared**: таргет `androidTarget()` за свойством `athletica.mobile`.
- **Сборка**: плагины Android Gradle Plugin и Compose Multiplatform в `libs.versions.toml`,
  репозиторий `google()`, условное подключение `:mobile` в `settings.gradle.kts`, CI-job
  `mobile.yml`, мобильные проверки в `CLAUDE.md`.
- **Внешние зависимости**: Firebase (FCM SDK) — проект Firebase заводит `client-app-push`.
- **Зависимости**: клиентское API из `client-cabinet-web` (все шаги) — для всех экранов;
  схемы переписки из `client-app-chat` — для экранов переписки; маршруты токена из
  `client-app-push` — для push. До готовности сервера экраны работают на заглушках ответов.
