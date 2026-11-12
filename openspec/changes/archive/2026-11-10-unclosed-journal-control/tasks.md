## 1. Схема БД и общие схемы

- [x] 1.1 Миграция `0081-attendance-control.sql`: `attendance_control_settings`, `session_overdue_tasks` (отложенный FK на задачу), частичный индекс `sessions(date) WHERE status = 'scheduled'`; проверка — тесты на TestPostgres стартуют с применённой миграцией
- [x] 1.2 `shared`: `AttendanceControlSettingsResponse`, `UpdateAttendanceControlSettingsRequest`, `UnclosedSessionsRequest`/`UnclosedSessionsResponse`/`UnclosedSessionItem`, `DashboardWidget.UnclosedSessions` в `DashboardSettings.default()`; проверка — `./gradlew compileKotlin`, тест `withDefaults()` добавляет новый тип к старым настройкам

## 2. Настройки контроля посещаемости (сервер)

- [x] 2.1 `domain/attendancecontrol/`: `JournalCloseDelay` (smart constructor, 15..10080, дефолт 120), `AttendanceControl`/`AttendanceControls` и Db-реализации (дефолты без строки, upsert); проверка — DB-тест: дефолты, сохранение и чтение, изоляция организаций, отказ на 5 и 10081 минут
- [x] 2.2 Маршруты `GET /org/attendance-control`, `POST /org/attendance-control/update`, регистрация в `Di`/`Application`; проверка — тест маршрута: ошибка `INVALID_JOURNAL_CLOSE_DELAY` без изменения настроек

## 3. Задача по незакрытому журналу (сервер)

- [x] 3.1 Сообщения `OverdueJournalTaskTitle`/`OverdueJournalTaskDescription` (ru/en) в `Messages`
- [x] 3.2 `usecases/sessions/OverdueJournalScheduler`: выборка по всем организациям (окно, настройки, «только недавние», часовой пояс), гард `session_overdue_tasks` + системная задача без исполнителя в транзакции на занятие; проверка — DB-тест с подменённым `Clock`: задача после окна, нет задачи до окна, нет для проведённого/отменённого, нет повторной (в т.ч. после удаления задачи), выключено в настройках, окно 30 минут, давнее занятие (>суток) без задачи, часовой пояс `Asia/Yekaterinburg`
- [x] 3.3 Запуск `overdueJournalScheduler.run()` в `Application.module()` и сборка в `Di`; проверка — `./gradlew build`

## 4. Проекция незакрытых занятий (сервер)

- [x] 4.1 `read/home/UnclosedSessionsView` + `DbUnclosedSessionsView` (SQL + отдельный COUNT, скоуп org + branch группы), регистрация в `ReadViews`; проверка — DB-тест в `read/home/`: только свой филиал, только закончившиеся запланированные, порядок от недавних, лимит и `total`, часовой пояс
- [x] 4.2 Маршрут `GET /home/unclosed-sessions` с нормализацией лимита 1..50; проверка — `./gradlew build`

## 5. Веб-клиент

- [x] 5.1 Перегенерировать контракты (`cd web && npm run contracts`) и закоммитить `web/src/api/generated/`
- [x] 5.2 Панель `settings/attendanceControl/AttendanceControlSheet.tsx`, панель `attendance-control` в `settingsSearch.ts`, пункт в разделе «Занятия и абонементы» `SettingsPage`, строки ru/en; проверка — vitest: дефолты (включено, «2 часа»), выбор недоступен при выключенном переключателе, сохранение отправляет минуты, ошибка остаётся в панели, нестандартное значение показано вариантом
- [x] 5.3 Виджет `home/UnclosedSessionsWidgetCard.tsx`, тип в `dashboardSettings.ts` (`WIDGET_TYPES`, `defaultWidgetOf`, `defaultTitleKey`), лимит в `WidgetSettingsButton`, отрисовка в `HomePage`, строки ru/en; проверка — vitest: список со ссылками на занятия, общее число при превышении лимита, пустое и ошибочное состояния, `withDashboardDefaults` добавляет виджет

## 6. Проверка

- [x] 6.1 `./gradlew build`, `./gradlew ktlintFormat`, в `web/` — `npm run format` и `npm run check` проходят
- [x] 6.2 В превью: настройки «Контроль посещаемости» сохраняются и переживают перезагрузку; виджет на главной показывает прошедшее непроведённое занятие и ведёт на его карточку
