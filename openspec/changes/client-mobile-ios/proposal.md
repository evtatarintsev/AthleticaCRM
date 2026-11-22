## Why

Общие экраны клиентского приложения пишутся в `client-mobile-android` на Compose
Multiplatform. Чтобы те же экраны работали на iPhone, нужны iOS-таргеты, Xcode-оболочка и
платформенные сервисы: Keychain, APNs, выбор файлов. Compose Multiplatform на iOS моложе, чем
на Android, поэтому iOS-сборку проверяем рано, а не после всех экранов.

## What Changes

- iOS-таргеты (`iosArm64`, `iosSimulatorArm64`) в `shared` и `:mobile` за тем же свойством
  `athletica.mobile`.
- Xcode-проект `mobile/iosApp` — оболочка, которая показывает общий Compose-интерфейс.
- iOS-реализации платформенных частей: сеанс в Keychain только на этом устройстве, Ktor-движок
  Darwin, выбор изображений и PDF, открытие ссылок.
- Push через APNs: запрос разрешения, отправка токена, открытие переписки по нажатию.
- CI-job на macOS: сборка iOS-фреймворка и тесты в симуляторе.
- **Не входит**: общие экраны (в `client-mobile-android`), публикация в App Store и TestFlight
  ([#274](https://github.com/evtatarintsev/AthleticaCRM/issues/274)).

## Capabilities

### New Capabilities
- `client-app-ios`: требования к iOS-версии клиентского приложения — поддерживаемые версии,
  сеанс только на этом устройстве, разрешение на уведомления.

### Modified Capabilities

## Impact

- **mobile**: `iosMain` (`actual`-реализации), `mobile/iosApp` (Xcode-проект).
- **shared**: таргеты `iosArm64()`, `iosSimulatorArm64()` за свойством `athletica.mobile`.
- **Сборка**: `ktor-client-darwin` в `libs.versions.toml`, CI-job на macOS-раннере.
- **Внешние зависимости**: учётная запись Apple Developer Program — push на реальном устройстве
  без платной учётной записи не работает (заводится в рамках
  [#274](https://github.com/evtatarintsev/AthleticaCRM/issues/274), этот пункт нужен раньше).
- **Зависимости**: `client-mobile-android` — модуль `:mobile`, сетевой слой и `expect`-интерфейсы
  (задачи 1.1, 2.1, 2.2); маршруты токена из `client-app-push` — для push.
