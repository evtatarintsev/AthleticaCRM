## Context

Мотивация — proposal.md, требования — `specs/client-app-ios/spec.md` и общие `client-app-ui` из
`client-mobile-android`. Общие экраны, сетевой слой и `expect`-интерфейсы (`SessionStore`,
выбор файлов, push-токен, открытие ссылок) — design `client-mobile-android` §3–§6; здесь только
их iOS-реализации и оболочка.

## Goals / Non-Goals

**Goals:**
- Ни одного экрана, написанного отдельно для iOS: отличия — только в `iosMain` и оболочке.
- iOS-сборка и запуск в симуляторе — первым шагом, сразу после каркаса `:mobile`, чтобы
  ограничения Compose Multiplatform на iOS всплыли до того, как написаны все экраны.

**Non-Goals:**
- iPad-раскладка (приложение запускается на iPad в режиме iPhone), виджеты, Apple Watch.
- Подпись релиза и TestFlight — [#274](https://github.com/evtatarintsev/AthleticaCRM/issues/274).

## Decisions

### 1. Оболочка — SwiftUI с `ComposeUIViewController`

`mobile/iosApp` — минимальный Xcode-проект: `@main` App, `UIViewControllerRepresentable` с
`MainViewController()` из `iosMain`, `AppDelegate` для APNs. Фреймворк `:mobile` подключается
через задачу Gradle `embedAndSignAppleFrameworkForXcode` в фазе сборки Xcode, без CocoaPods.

- *Альтернатива — CocoaPods/SPM-пакет:* не нужен, пока внешних iOS-зависимостей нет (APNs —
  системный фреймворк, Firebase на iOS не используется — design `client-app-push` §3).

### 2. Keychain с `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`

`actual SessionStore` пишет одну запись Keychain. `ThisDeviceOnly` исключает перенос через
iCloud-связку и восстановление на другом устройстве — это и есть требование «Сеанс только на
этом устройстве», согласованное с проверкой на новом устройстве в ядре. `AfterFirstUnlock` —
чтобы обновление токена работало при обработке push в фоне.

Keychain переживает удаление приложения: при первом запуске после установки (флаг в
`NSUserDefaults`, который удаление стирает) запись Keychain удаляется.

### 3. Сеть — `ktor-client-darwin`

Общий сетевой слой без изменений, движок Darwin (URLSession) — системные прокси, ATS и
сертификаты работают как у остальных приложений.

### 4. APNs

Разрешение запрашивается после первого подключения профиля (`UNUserNotificationCenter`),
`registerForRemoteNotifications` → токен в hex → `/api/client-app/push/token` с платформой
`IOS`. Entitlement `aps-environment = development` для отладочных сборок; production — при
релизе (#274). Нажатие на уведомление передаёт `orgId`/`clientId` из `userInfo` в общую
навигацию.

### 5. Выбор файлов и ссылки

Изображения — `PHPickerViewController`, PDF — `UIDocumentPickerViewController`; ссылки на
условия и политику — `SFSafariViewController`. Всё за `expect`-интерфейсами из
`client-mobile-android`.

### 6. Минимальная версия — iOS 16

Покрывает подавляющее большинство iPhone в использовании; поддержка Compose Multiplatform и
нужных API (`PHPicker`, современный `UNUserNotificationCenter`) без обходных путей.

### 7. CI на macOS

Отдельный job (macOS-раннер): `-Pathletica.mobile=true :mobile:iosSimulatorArm64Test` и
`xcodebuild` оболочки для симулятора без подписи. Запускается при изменениях `mobile/` и
`shared/`.

## Risks / Trade-offs

- [Ограничения Compose Multiplatform на iOS: клавиатура, safe area, жест «назад»] → проверяются
  в задаче 1.x на экранах входа и ввода дат до остальных экранов; найденное правится в общем
  коде `client-mobile-android`.
- [Push на реальном iPhone требует платной учётной записи Apple] → завести её раньше остальной
  работы #274; до этого push проверяется только как регистрация в симуляторе.
- [macOS-раннеры CI дороже] → job только при изменениях `mobile/` и `shared/`.
