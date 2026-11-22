# Design

## Context

Мотивация и выбранный вариант экрана — в `proposal.md`; требования — в `specs/client-list` и
`specs/web-client/clients-list`.

Текущее состояние, от которого отталкивается решение:

- `DbClientListView` собирает страницу одним SQL: баланс через CTE `bal`, группы и контакты —
  коррелированными подзапросами `jsonb_agg` в списке `SELECT`; `total` — отдельный `COUNT` по тому
  же `FROM_WHERE`. Тот же `page()` вызывает экспорт `clients/export` с `limit = null`.
- Признаки активного абонемента на дату считает общий `ActiveStatus.of` (`shared`). Им уже
  пользуется `DbGroupRosterView`: «сегодня» берётся как `(:now AT TIME ZONE o.timezone)::date`, где
  `now` приходит в `GroupRosterQuery` из маршрута.
- Неоплаченное занятие (`membership-debits`): `session_attendance.presence = 'present'`,
  `sessions.status = 'completed'`, нет `membership_debits` с `reversed_at IS NULL`. Тот же предикат
  уже записан в `DbGroupRosterView` и в проекции `client-unpaid-sessions`.
- Таблица `memberships`: `state` (`INACTIVE`/`ACTIVE`/`CLOSED`/`REVOKED`), `activation_mode`,
  `activate_on`, `start_date`/`end_date` (у неактивированного обе `NULL` — check-ограничение),
  `sessions_total`/`sessions_remaining` (`NULL` — безлимит), `issued_on`, `created_at`.
- Колонки таблицы и поля экспорта сейчас один каталог: `ClientField` в `shared` (birthday, gender,
  groups, balance) и его ручная копия `CLIENT_FIELD_KEYS` в `web/src/clients/clientColumns.ts`,
  которой пользуются и `ClientColumnsDialog`, и `ClientExportDialog`.
- Настройки отображения — JSONB `user_display_settings.settings`; `ClientsDisplaySettings.columns`
  по умолчанию — gender, birthday, balance. Сортировка списка живёт в URL (`clientListSearch`),
  `settings.clients.sort` вебом не читается.
- Возраст в карточке клиента уже считается `ageOn()` (`web/src/clients/card/clientOverview.ts`),
  строка — `clientCard.meta.age`.

## Goals / Non-Goals

**Goals:**

- Абонементы и число неоплаченных занятий приходят в том же запросе страницы, без N+1 и без
  изменения `total`, фильтров и сортировки.
- Признаки абонемента выводятся только `ActiveStatus.of`; в SQL их копии нет.
- Каталог колонок таблицы отделён от каталога полей экспорта, экспорт не меняется.

**Non-Goals:**

- Фильтр или сортировка списка по абонементам («без абонемента», «заканчивается»).
- Метка заморозки в списке: заморозка уже сдвинула `end_date`, отдельного вида у метки нет.
- Ограничение колонки «Группы» текущим филиалом — группы остаются как сейчас.
- Отказ от сортировки `BIRTHDAY` в API: `ClientSortField.BIRTHDAY` и `ClientSortColumn.BIRTHDAY`
  остаются, из веба уходит только заголовок-кнопка.
- Общий SQL-фрагмент «неоплаченное занятие» для всех проекций — см. Risks.

## Decisions

### 1. Собственная схема абонемента строки, состояние — sealed

В `ClientListItem` добавляются `memberships: List<ClientListMembership>` и `unpaidSessions: Int`.

```
ClientListMembership
  id, name, sessionsTotal?, sessionsRemaining?
  state: ClientListMembershipState          (дискриминатор "type")
    +-- "inactive": activationMode, activateOn?
    +-- "active":   startDate, endDate, termPosition, endingSoon[], closePending
```

Почему не `MembershipSchema` / `MembershipStateSchema`: первая тащит продажу, цену, группы; вторая
допускает `closed`/`revoked`, которых в списке быть не может, а у `active` есть `freeze`, для
которого пришлось бы грузить заморозку или отдавать заведомо ложный `null`. Почему не плоская
схема, как `GroupRosterMembership`: у плоской `termPosition`/`startDate` nullable «потому что
неактивирован», вебу пришлось бы это угадывать; sealed-вариант даёт исчерпывающий `switch` в
`web` и делает недопустимые сочетания непредставимыми. Сохранённое состояние кодируется вариантом,
`activationMode` нужен только неактивированному («С первого посещения» против «Начнётся ДД.ММ»).

### 2. SQL отдаёт сырые поля, признаки и порядок — Kotlin

В `SELECT` страницы добавляются два выражения (только в запросе страницы, не в `COUNT`):

- `memberships` — коррелированный `jsonb_agg` по `memberships m WHERE m.client_id = c.id AND
  m.org_id = c.org_id AND m.state IN ('ACTIVE','INACTIVE')` с сырыми колонками и порядком
  `ORDER BY CASE` по части (0 — активный не «к закрытию», 1 — `end_date < :today OR
  sessions_remaining <= 0`, 2 — неактивированный), затем `end_date NULLS LAST, issued_on,
  created_at, id`;
- `unpaid_sessions` — `COUNT(*)` по предикату неоплаченного занятия с
  `JOIN groups g ON g.id = s.group_id AND g.branch_id = :branchId`.

JSON декодируется в приватный `@Serializable` row-тип внутри `DbClientListView`; для `ACTIVE`
вызывается `ActiveStatus.of(start, end, remaining, today)`, порядок списка — SQL.

Ступень «к закрытию» в `CASE` — копия `ActiveStatus.closePending`, как в `DbGroupRosterView`:
проекция постраничная, а сортировать в Kotlin по признаку из `shared` CLAUDE.md разрешает только
в непостраничной проекции. Копию держит в согласии тест «Действующий раньше неактивированного и
к закрытию».

Альтернатива «отдельный запрос абонементов по `client_id = ANY(:ids)`» отклонена: требование
спеки — один запрос страницы, и текущая проекция уже строится на коррелированных подзапросах.

### 3. «Сегодня» — из `ClientListQuery.today`

`ClientListQuery` получает обязательное `today: LocalDate` без значения по умолчанию; маршруты
`clients/list` и `clients/export` берут его из `OrgCalendar.today()`, как финансовые маршруты,
`toQuery(today)` принимает его аргументом. Так тесты задают дату сценария («сегодня 20.11»), а
правило часового пояса организации остаётся в одном месте — `OrgCalendar`.

Экспорт абонементы и неоплаченные занятия не выводит, поэтому передаёт
`withMemberships = false`: подзапросы заменяются константами `'[]'` и `0`, и выгрузка всей базы
не считает историю посещаемости каждого клиента.

### 4. Каталог колонок отдельно от экспорта

- `shared`: новый `enum class ClientListColumn(apiKey)` — `GROUPS("groups")`,
  `MEMBERSHIPS("memberships")`, `BALANCE("balance")`; `ClientsDisplaySettings.columns` по
  умолчанию — эти три ключа. `ClientField` не меняется и остаётся каталогом экспорта.
- `web`: в `clientColumns.ts` `ClientFieldKey` → `ClientColumnKey = "groups" | "memberships" |
  "balance"`, `CLIENT_COLUMN_KEYS`; список полей экспорта (`birthday`, `gender`, `groups`,
  `balance`) переезжает в `ClientExportDialog` вместе с подписями. Ключи `gender`/`birthday` в
  сохранённых колонках `resolveClientColumns` отбросит как неизвестные.
- Ключи `groups`/`balance` совпадают в обоих каталогах намеренно — значение в настройках и в
  запросе экспорта прежнее.

### 5. Сброс колонок — миграция Liquibase

Новый changeset `0090-reset-client-list-columns.sql` (номер — следующий свободный на момент
реализации):

```sql
UPDATE user_display_settings
   SET settings = settings #- '{clients,columns}'
 WHERE settings #> '{clients,columns}' IS NOT NULL;
```

Ключ удаляется, а не перезаписывается списком: умолчание остаётся в одном месте —
`ClientsDisplaySettings`, а `DisplaySettings` уже декодирует отсутствующие поля значениями по
умолчанию. Сохранённые виды (`savedViews`) колонок не хранят и не трогаются.

### 6. Метка абонемента — чистая функция в `web`

`clientListMembershipLabel(m)` в `web/src/clients/clientListMembership.ts` возвращает
`{ tone, icon, parts }` по порядку правил из спеки (к закрытию → не начался/не активирован →
заканчивается → действует) исчерпывающим `switch` по `state.type`. Компонент только рисует
результат; строки — из `ru`/`en`. Отдельно от `membershipView` карточки: тот принимает
`MembershipSchema` и различает `closed`/`revoked`, здесь другой вход и меньше фаз.

Вёрстка строки:

```
 компьютер (>= 768 px)
 +---+----------------------+--------------+----------------------------+------------+---+
 |[ ]| (A) Анна Петрова     | [Мини]       | [Мини 8 · 8 из 12 · до 03.12]|  -1 200 ₽ | / |
 |   |     Ж · 9 лет        | [Юниор]      | [Юниор · Безлимит · до 31.12]| 2 неоплач.|   |
 +---+----------------------+--------------+----------------------------+------------+---+

 телефон (< 768 px)
 +------------------------------------------+
 | [ ] (A) Анна Петрова            -1 200 ₽ |
 |         Ж · 9 лет             2 неоплач. |
 | +--------------------------------------+ |
 | | [Мини] [Юниор]                       | |  <- подложка: «Группы» и/или «Абонемент»
 | | [Мини 8 · 8 из 12 · до 03.12]        | |
 | | [Юниор · Безлимит · до 31.12]        | |
 | +--------------------------------------+ |
 +------------------------------------------+
```

Пол и возраст под именем: `ageOn()` из карточки и `clientCard.meta.age`; короткий пол — новые
ключи словаря.

## Risks / Trade-offs

- [Третья копия предиката «неоплаченное занятие» (roster, client-unpaid-sessions, список)] →
  DB-тест списка повторяет сценарии `client-unpaid-sessions` (сторно, «не пришёл», непроведённое,
  разовое посещение вне групп, другой филиал). Выносить общий SQL-фрагмент — когда появится
  четвёртый потребитель или изменится определение.
- [Коррелированные подзапросы на каждую строку страницы] → вычисляются только для строк после
  `LIMIT` (не ключи сортировки); есть индексы `idx_memberships_client`,
  `idx_session_attendance_client`, `uq_membership_debits_active`. Экспорт без `LIMIT` считает их
  для всех клиентов — приемлемо при текущих объёмах, сигнал к флагу в `ClientListQuery`, если
  экспорт замедлится.
- [`today` вычисляется в запросе страницы, а при `total = 0` запрос не выполняется] → абонементов
  тогда нет, `today` не нужен.
- [Неактивированный абонемент с `ON_DATE`, дата которого уже прошла, но фон ещё не активировал] →
  метка «Начнётся ДД.ММ» с прошедшей датой до прогона фона; поведение совпадает с карточкой.
- [Миграция сбрасывает личные наборы колонок у всех] → это требование спеки; контакты и доп. поля
  сотрудник добавит заново через «Колонки».

## Migration Plan

1. Деплой сервера с миграцией: changeset удаляет `clients.columns`, новые ответы уже содержат
   `memberships`/`unpaidSessions`.
2. Веб в том же релизе (собирается вместе с сервером). Старый веб на новом API работает:
   zod-схемы объектов не строгие к лишним полям.
3. Откат: откат кода без отката данных — старый код декодирует настройки без `columns` своим
   умолчанием (gender, birthday, balance). Отдельный rollback changeset не нужен.
