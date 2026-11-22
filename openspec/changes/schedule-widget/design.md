# Design

## Context

- Расписание CRM читает `read/schedule/DbScheduleView`. Проекции слоя `read/` принимают
  `EmployeeRequestContext`, а у посетителя виджета сотрудника нет. `ScheduleSessionSchema`
  отдаёт внутренние id и служебные признаки (`coachesOverridden`, `isManual`, `colorKey`),
  поэтому для публичного ответа она не подходит.
- Общие SQL-фрагменты по занятиям лежат в `read/SessionSql.kt`. Там же тренеры занятия
  с учётом замены состава (`COACHES_JSON`).
- Маршруты без входа уже есть: `checkoutRoutes` подключён в `Application.kt` вне
  `authenticate("auth-jwt")`.
- «Сегодня» организации считает `OrgCalendar` по `organizations.timezone`, но ему нужен
  `RequestContext`. По правилам `read/` момент `now` передаётся в `XxxQuery` из routes.
- В `nginx/prod.conf.template` на хосте `app.` задан только `X-Robots-Tag`. Запретов на
  встраивание нет ни для CRM, ни для чего-либо ещё.
- Веб-маршруты лежат в `web/src/app/router.tsx`. Публичные страницы (`/login`, `/sign-up`)
  находятся вне защищённого `AppLayout`.

## Goals / Non-Goals

**Goals:**
- Публичная проекция без контекста сотрудника, весь отбор и скрытие полей — в SQL.
- Виджет как агрегат своего домена с аудитом изменений.
- Запрет встраивания CRM без поломки встраивания виджета.

**Non-Goals:**
- Запись на занятие, заявки, данные клиентов.
- Интеграция с API ВКонтакте и VK Mini Apps.
- Автоподстройка высоты iframe (`postMessage`), темы и стилизация под сайт клуба.
- Ограничение частоты запросов к публичному API (см. Risks).

## Decisions

### 1. Домен `domain/widgets`

Агрегат `ScheduleWidget` (интерфейс + `DbScheduleWidget`), репозиторий `ScheduleWidgets`
(`DbScheduleWidgets`: `new`, `byId`), аудит по образцу `AuditGroups`.
Поля: `id`, `branchId`, `name: NonBlankName`, `publicKey`, `enabled`,
`horizon: WidgetHorizon`, `showCoaches`, `showHall`, `showDisciplines`,
`excludedGroups: List<GroupId>`.

- `WidgetHorizon` — smart constructor по правилам CLAUDE.md: целое число дней от 7 до 90,
  по умолчанию 30.
- Принадлежность исключённых групп филиалу проверяется при `save` одним запросом
  `COUNT(*) FROM groups WHERE id = ANY(:ids) AND org_id = :orgId AND branch_id = :branchId`.
  Несовпадение — `CommonDomainError("GROUP_NOT_FOUND", ...)`.
- Право проверяется в домене: `CAN_MANAGE_WIDGETS` на всех командах.
- Удаление физическое. Исключённые группы удаляются каскадом. Аудит фиксирует удаление.

*Альтернатива:* настройки виджета в JSONB одной колонкой. Отклонено: исключённые группы —
ссылки на `groups`, им нужен FK с каскадом при удалении группы.

### 2. Схема БД

```
schedule_widgets(
  id uuid pk, org_id fk, branch_id fk,
  name varchar(255), public_key varchar(64) unique,
  enabled bool, horizon_days int check (7..90),
  show_coaches bool, show_hall bool, show_disciplines bool,
  created_at timestamptz)
schedule_widget_excluded_groups(
  widget_id fk on delete cascade, group_id fk on delete cascade,
  pk(widget_id, group_id))
groups.shown_in_widgets bool not null default true
```

Ключ — 32 случайных байта из `SecureRandom` в base64url (43 символа). Перевыпуск — `UPDATE`
ключа. Уникальный индекс одновременно служит индексом поиска по ключу.

*Альтернатива:* использовать `id` виджета (UUIDv7) как публичный ключ. Отклонено: v7
частично предсказуем по времени, и его нельзя перевыпустить без смены сущности.

### 3. Признак группы

`Group` получает `shownInWidgets` и `withShownInWidgets(Boolean)`. В `GroupCreateRequest`
поле необязательное со значением по умолчанию `true`. В `EditGroupRequest` поле
**обязательное**: редактирование заменяет все поля группы целиком, и значение по умолчанию
молча включало бы показ у скрытой группы. `GroupDetailResponse` отдаёт признак форме.

### 4. Публичная проекция `read/widgets/PublicScheduleView`

```kotlin
interface PublicScheduleView {
    context(tr: Transaction, raise: Raise<AppError>)
    suspend fun schedule(query: PublicScheduleQuery): PublicScheduleResponse
}
data class PublicScheduleQuery(val key: String, val from: LocalDate?, val to: LocalDate?, val now: Instant)
```

Два SQL:
1. По ключу: настройки виджета, `org_id`, `branch_id` и
   `today = (:now AT TIME ZONE o.timezone)::date`. Если строки нет или `enabled = false` —
   `raise(CommonDomainError("WIDGET_UNAVAILABLE", ...))`, одинаково для всех случаев.
2. Занятия филиала за `[max(from, monday(today)), min(to, today + horizon)]`, где
   `g.shown_in_widgets` и группа не в `schedule_widget_excluded_groups`. Зал, дисциплины
   и тренеры выбираются через `CASE WHEN :showX`, поэтому скрытые поля не покидают базу.
   Тренеры — по `SessionSql.COACHES_JSON`, в ответ попадают только имена.

Два запроса вместо одного: границы периода и флаги полей нужны второму запросу как
параметры, а сам ответ содержит их для навигации на фронте. Склейка в один SQL через CTE
дала бы тот же план и менее читаемый запрос.

Ответ (`shared/.../api/schemas/widgets/`):

```
PublicScheduleResponse(today, periodStart, periodEnd, sessions: List<PublicScheduleSessionSchema>)
PublicScheduleSessionSchema(date, startTime, endTime, groupName,
                            hall: String?, disciplines: List<String>?, coaches: List<String>?,
                            cancelled: Boolean)
```

`null` означает, что поле выключено в виджете. Пустой список означает, что поле включено,
но у занятия нет данных.

*Альтернатива:* обобщить `ScheduleView` до контекста без сотрудника. Отклонено: у экранов
разные права, фильтры и состав ответа, а общий SQL уже вынесен в `SessionSql`.

### 5. Маршруты

- `GET /api/public/schedule-widget?key=..&from=..&to=..` подключается в `Application.kt`
  рядом с `checkoutRoutes`, вне `authenticate`. Транзакция открывается напрямую через
  `Database`. Ошибка отдаётся через `respondError` (400 с кодом `WIDGET_UNAVAILABLE`),
  ответ одинаков для всех причин. Заголовок `Cache-Control: public, max-age=60`.
- Управление (`/api/schedule-widgets/list|detail|create|edit|regenerate-key|delete`)
  работает с входом, идентификатор передаётся в теле или query по правилам REST проекта.
  Список и карточку читает проекция `read/widgets/WidgetListView`, команды идут через
  `ScheduleWidgets`.

### 6. Web

- `/widget/$key` в `router.tsx` создаётся вне `AppLayout` и без проверки сессии. Ответ
  декодируется сгенерированной zod-схемой. Неделя или день выбирается по ширине (тот же
  брейкпоинт, что у страницы «Расписание»). Компоненты сетки переиспользуются, если они
  не завязаны на схему CRM, иначе пишется лёгкая своя разметка.
- `/settings/schedule-widgets`: список и шит создания/редактирования по образцу
  `/settings/halls`. Пункт меню скрыт без права `CAN_MANAGE_WIDGETS`. Предпросмотр —
  iframe на `/widget/<ключ>`, который перезагружается после сохранения. Код iframe
  и инструкция для ВКонтакте взяты из словарей `ru.ts`/`en.ts`.
- Переключатель «Показывать в виджетах» в `GroupCreateSheet` и в форме редактирования группы.

### 7. Встраивание (nginx)

На сервере `app.` в `prod.conf.template` и `dev.conf`:

```
add_header Content-Security-Policy "frame-ancestors 'none'" always;   # уровень server
location /widget/ {
    add_header Content-Security-Policy "frame-ancestors *" always;
    add_header X-Robots-Tag "noindex, nofollow" always;
    ...проксирование на frontend как в location /...
}
```

`add_header` в `location` отменяет унаследованные заголовки сервера, поэтому
`X-Robots-Tag` повторяется явно. Используется `frame-ancestors`, а не `X-Frame-Options`:
последний не умеет «разрешить всем» для одного пути. Предпросмотр в настройках не ломается:
страница виджета разрешает любых предков, включая сам `app.`.

## Risks / Trade-offs

- [Публичный API без авторизации можно опрашивать массово] → ключ не подбирается,
  ответ кэшируется на 60 секунд, данные в ответе и так публичные. Если появится нагрузка,
  нужно добавить `limit_req` в nginx на `/api/public/`.
- [Группа с личными данными в названии попадёт в виджет, если признак забыли выключить]
  → предпросмотр в карточке виджета показывает итоговый состав до публикации ссылки.
- [`frame-ancestors 'none'` может сломать существующее встраивание CRM куда-то] →
  в проекте таких сценариев нет. Откат — убрать одну строку из конфига nginx.
- [Названия залов и групп могут оказаться служебными] → зал отключается в настройках
  виджета, группа скрывается признаком.

## Migration Plan

1. Миграция Liquibase: таблицы виджетов и колонка `groups.shown_in_widgets` со значением
   по умолчанию `true`. Существующие группы остаются видимыми, но виджетов ещё нет,
   поэтому ничего не публикуется.
2. Деплой server и web, затем перезагрузка nginx с новым шаблоном.
3. Откат: выключить или удалить виджеты, убрать строки CSP из nginx. Таблицы можно
   оставить.
