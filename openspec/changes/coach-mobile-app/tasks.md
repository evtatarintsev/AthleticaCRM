## 1. shared под мобильные таргеты

- [ ] 1.1 Добавить в `shared` таргеты `androidTarget` (jvmTarget 17), `iosArm64`, `iosSimulatorArm64`; проверка — `./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileAndroidMain` (или аналог для выбранного Android-плагина) и `./gradlew build` проходят
- [ ] 1.2 Исправить код `commonMain`, не собирающийся под Kotlin/Native, если найден в 1.1; проверка — те же команды и `./gradlew :shared:allTests`
- [ ] 1.3 Подключать мобильные модули в `settings.gradle.kts` только при наличии Android SDK; проверка — `./gradlew build` без `ANDROID_HOME` собирает server и shared

## 2. Сервер: изменение заметки

- [ ] 2.1 Схема `UpdateSessionNotesRequest` в `shared/api/schemas/sessions`; проверка — компиляция
- [ ] 2.2 Команда изменения заметки в домене `sessions` (нормализация пустой строки в `null`, лимит 2000 символов, аудит прежнего и нового значения) и DB-тест в `server/src/test/.../domain/` на сценарии из `specs/session-notes`; проверка — `./gradlew test --tests "*SessionNotes*"`
- [ ] 2.3 Маршрут `POST /sessions/update-notes` с ответом `SessionDetailResponse` и контрактом; проверка — `cd web && npm run contracts` обновляет `web/src/api/generated/`, `npm run check` проходит
- [ ] 2.4 Список мобильных маршрутов (путь + метод) в `shared` и серверный тест, что каждый из них зарегистрирован с тем же методом; проверка — тест проходит и падает при искажении пути

## 3. mobile/core

- [ ] 3.1 Модуль `:mobile:core` (KMP: android, iosArm64, iosSimulatorArm64) с Ktor client (OkHttp / Darwin), kotlinx-serialization, coroutines и SKIE, XCFramework-задача для iOS; проверка — `./gradlew :mobile:core:assembleXCFramework` и сборка Android-артефакта
- [ ] 3.2 `TokenStore` (expect/actual: Keychain, Keystore + DataStore); проверка — платформенные тесты сохранения/чтения/очистки
- [ ] 3.3 `ApiClient`: маршруты из таблицы design.md, Bearer + авто-refresh через `X-Refresh-Token`, кодирование GET-схем в query, ошибки как `Either<ApiError, T>`; проверка — тесты на `ktor-client-mock` (успех, `ErrorResponse`, 401 → refresh → повтор, неудачный refresh → очистка токенов)
- [ ] 3.4 Базовый ViewModel на `CoroutineScope` и `LoginViewModel` (логин, выбор филиала при нескольких); проверка — юнит-тесты сценариев «один филиал», «несколько филиалов», «неверный пароль»
- [ ] 3.5 `AppSessionViewModel`: восстановление входа при запуске, смена филиала, выход; проверка — юнит-тесты
- [ ] 3.6 `DayScheduleViewModel`: занятия тренера за день через `/schedule/list` с `employeeIds = [me]`, переходы по дням, «сегодня», пустое и ошибочное состояние с повтором; проверка — юнит-тесты
- [ ] 3.7 `SessionCardViewModel`: параллельная загрузка журнала, деталей и меток; отметка с локальной проверкой метки и откатом при ошибке; массовая отметка; запрет отметок для отменённого и снятия отметки для проведённого; заметка; проверка — юнит-тесты на сценарии из `specs/mobile-client`

## 4. Android-приложение

- [ ] 4.1 Модуль `:mobile:android` (Compose, Material 3), адрес сервера через `BuildConfig` (эмулятор — `10.0.2.2`), строки ru/en в ресурсах; проверка — `./gradlew :mobile:android:assembleDebug`
- [ ] 4.2 Экраны входа и выбора филиала, главный экран со сменой дня, карточка занятия с отметками, массовой отметкой и заметкой, переключение филиала и выход; проверка — ручной прогон сценариев `specs/mobile-client` на эмуляторе против локального сервера

## 5. iOS-приложение

- [ ] 5.1 Xcode-проект `mobile/ios` на SwiftUI, подключение XCFramework из `:mobile:core` фазой сборки, адрес сервера в `Info.plist`, строки ru/en в `Localizable.xcstrings`; проверка — сборка в симуляторе
- [ ] 5.2 Те же экраны, что в 4.2, поверх ViewModel-ей через SKIE; проверка — ручной прогон сценариев `specs/mobile-client` в симуляторе против локального сервера

## 6. Документация и проверки

- [ ] 6.1 `mobile/README.md`: требования (Android SDK, Xcode), сборка и запуск обеих платформ против локального сервера; строка о модуле `mobile` в CLAUDE.md («Modules»); проверка — по README собирается чистый клон
- [ ] 6.2 Финальные проверки: `./gradlew build`, `./gradlew ktlintFormat`, `cd web && npm run check`; все проходят
