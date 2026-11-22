## 1. Сборка и оболочка

- [ ] 1.1 Добавить `iosArm64()` и `iosSimulatorArm64()` в `shared` и `:mobile` за `athletica.mobile`, `ktor-client-darwin` в `libs.versions.toml`; проверить, что `./gradlew build` без свойства не меняется, а `-Pathletica.mobile=true :mobile:linkDebugFrameworkIosSimulatorArm64` собирает фреймворк
- [ ] 1.2 Xcode-проект `mobile/iosApp`: SwiftUI-оболочка с `ComposeUIViewController`, фаза `embedAndSignAppleFrameworkForXcode`, iOS 16 deployment target; проверить запуск в iOS-симуляторе и прохождение экранов входа и ввода дат (клавиатура, safe area, жест «назад»), найденные проблемы исправить в общем коде
- [ ] 1.3 CI-job на macOS: `:mobile:iosSimulatorArm64Test` и `xcodebuild` оболочки без подписи при изменениях `mobile/` и `shared/`; проверить зелёный прогон на ветке

## 2. Платформенные реализации

- [ ] 2.1 `actual SessionStore` на Keychain (`AfterFirstUnlockThisDeviceOnly`), удаление записи при первом запуске после установки; тест в симуляторе: сохранение, очистка, переустановка приложения открывает вход
- [ ] 2.2 Выбор изображений (`PHPicker`) и PDF (`UIDocumentPicker`), открытие ссылок в `SFSafariViewController`; проверить в симуляторе прикрепление файла в переписке и открытие политики ПД

## 3. Push через APNs

- [ ] 3.1 Запрос разрешения после первого подключения профиля, регистрация в APNs, отправка токена с платформой `IOS` на `/api/client-app/push/token` (после маршрутов `client-app-push`), состояние «выключены в настройках iPhone» в «Ещё»; тест отправки токена на `MockEngine`
- [ ] 3.2 Открытие переписки профиля по нажатию на уведомление; проверить на реальном iPhone с APNs sandbox вместе с `client-app-push` (нужна учётная запись Apple Developer Program)

## 4. Проверка

- [ ] 4.1 Сквозной сценарий на iPhone против тестового сервера с ядром: вход, подключение, расписание, «Не придём», ответ в переписке с push; `./gradlew build`, `./gradlew ktlintFormat` и `./gradlew -Pathletica.mobile=true :mobile:check` проходят
