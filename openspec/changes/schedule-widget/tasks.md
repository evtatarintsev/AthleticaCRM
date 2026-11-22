# Tasks

## 1. Схема БД и право

- [ ] 1.1 Добавить миграцию Liquibase: `schedule_widgets`, `schedule_widget_excluded_groups`, `groups.shown_in_widgets bool not null default true` (см. design §2); проверить, что `./gradlew test` поднимает схему без ошибок
- [ ] 1.2 Добавить `CAN_MANAGE_WIDGETS` в `Permission` и подписи права в `web/src/i18n/ru.ts`/`en.ts`; проверить, что тесты на права владельца (`employee-permissions`) проходят и право видно в `PermissionsSheet`

## 2. Признак группы «показывать в виджетах»

- [ ] 2.1 Добавить `shownInWidgets`/`withShownInWidgets` в `Group`, `DbGroup`, `DbGroups` и аудит группы; доменный тест: группа создаётся с `true` по умолчанию, признак меняется и сохраняется
- [ ] 2.2 Добавить поле в `GroupCreateRequest` (по умолчанию `true`), `EditGroupRequest` (обязательное) и `GroupDetailResponse`, связать в `GroupsRoutes` и `DbGroupDetailView`; проверить тестом проекции деталей группы
- [ ] 2.3 Добавить переключатель «Показывать в виджетах» в `GroupCreateSheet` и в форму редактирования группы; vitest-тест: значение уходит в запрос создания и редактирования

## 3. Домен виджетов

- [ ] 3.1 Создать `WidgetHorizon` (smart constructor 7..90, по умолчанию 30) с тестом границ 6/7/90/91
- [ ] 3.2 Создать `domain/widgets`: `ScheduleWidget`, `DbScheduleWidget`, `ScheduleWidgets`/`DbScheduleWidgets` (`new`, `byId`, `save`, `regenerateKey`, `delete`), генерацию ключа из `SecureRandom`, проверку `CAN_MANAGE_WIDGETS` и принадлежности исключённых групп филиалу; доменные тесты на сценарии `schedule-widgets` (значения по умолчанию, пустое название, группа другого филиала, перевыпуск ключа, удаление, отказ без права)
- [ ] 3.3 Добавить аудит виджетов по образцу `AuditGroups` и зарегистрировать репозиторий в `Di.kt`; тест: создание и удаление пишут записи аудита

## 4. Управление виджетами: API

- [ ] 4.1 Добавить схемы запросов и ответов виджетов в `shared/.../api/schemas/widgets/`
- [ ] 4.2 Создать `read/widgets/WidgetListView` + `DbWidgetListView` (список и карточка текущего филиала со скоупом по `org_id`/`branch_id`), зарегистрировать в `ReadViews`; DB-тест в `server/src/test/.../read/widgets/`: виджеты другого филиала и организации не видны
- [ ] 4.3 Добавить `ScheduleWidgetsRoutes` (`list`, `detail`, `create`, `edit`, `regenerate-key`, `delete`) и подключить внутри `authenticate`; проверить `./gradlew build`

## 5. Публичное расписание: API

- [ ] 5.1 Добавить `PublicScheduleResponse`/`PublicScheduleSessionSchema` в `shared` (design §4)
- [ ] 5.2 Создать `read/widgets/PublicScheduleView` + `DbPublicScheduleView` (два SQL, «сегодня» из `query.now` и часового пояса организации, скрытые поля через `CASE` в SQL) и зарегистрировать в `ReadViews`; DB-тест на сценарии `public-schedule-widget` и правила отбора `schedule-widgets`: исключённые и скрытые группы, другой филиал, границы периода, запрос за пределами периода, отменённое занятие, замена тренера, `null` у выключенных полей, `WIDGET_UNAVAILABLE` для неизвестного, выключенного и удалённого виджета
- [ ] 5.3 Добавить маршрут `GET /api/public/schedule-widget` вне `authenticate` рядом с `checkoutRoutes`, с `Cache-Control: public, max-age=60`; тест маршрута: ответ без сессии и одинаковая ошибка для всех недоступных ключей

## 6. Публичная страница виджета (web)

- [ ] 6.1 Перегенерировать контракты (`cd web && npm run contracts`) и закоммитить `web/src/api/generated/`
- [ ] 6.2 Добавить маршрут `/widget/$key` вне `AppLayout` без проверки сессии: неделя на широком экране, день на узком, навигация в пределах `periodStart..periodEnd`, пометка «отменено», сообщение об отсутствии занятий, страница «расписание недоступно»; строки взять из словарей; vitest-тесты на эти состояния и на то, что выключенные поля не выводятся

## 7. Настройки виджетов (web)

- [ ] 7.1 Добавить раздел `/settings/schedule-widgets` (пункт в `SettingsPage`/`settingsSearch`, скрытый без `CAN_MANAGE_WIDGETS`): список, шит создания и редактирования (название, включён, горизонт, поля, исключённые группы филиала); vitest-тест: пункт скрыт без права, запрос создания содержит настройки
- [ ] 7.2 Добавить в карточку виджета предпросмотр (iframe на `/widget/<ключ>`, перезагрузка после сохранения), копирование ссылки и кода iframe, инструкцию для ВКонтакте, перевыпуск ключа и удаление с подтверждением; vitest-тест: код iframe содержит текущий ключ, после перевыпуска ключ в коде меняется

## 8. Встраивание (nginx)

- [ ] 8.1 В `nginx/prod.conf.template` и `nginx/dev.conf` на сервере `app.` добавить `Content-Security-Policy: frame-ancestors 'none'`, а для `location /widget/` — `frame-ancestors *` с повтором `X-Robots-Tag` (design §7); проверить `curl -I` на dev-стенде: `/clients` отдаёт `'none'`, `/widget/x` отдаёт `*`, оба отдают `X-Robots-Tag`

## 9. Итоговая проверка

- [ ] 9.1 Выполнить `./gradlew build`, `./gradlew ktlintFormat`, а в `web/` — `npm run format` и `npm run check`; все проходят
- [ ] 9.2 На dev-стенде создать виджет, встроить его код iframe в локальную HTML-страницу с другого origin и проверить: расписание видно, тренеры скрыты по умолчанию, выключенная группа отсутствует, на ширине 375 px показывается день, iframe со страницей CRM браузер блокирует
