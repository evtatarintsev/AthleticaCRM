## Context

Мотивация — в [proposal.md](proposal.md), требования — в [specs/schedule/spec.md](specs/schedule/spec.md). Здесь только то, что определяет форму реализации.

Что уже есть в системе:

- `sessions` хранит занятия с `org_id`, `group_id`, датой, временем, `hall_id` (`NOT NULL`) и статусом (PostgreSQL ENUM `session_status`); **колонки филиала нет** — филиал живёт на `groups.branch_id`. `group_id` объявлен `ON DELETE SET NULL`, то есть занятие может остаться без группы.
- Связи «многие ко многим»: `session_employees` (тренеры занятия), `group_employees` (тренеры группы), `group_disciplines` (дисциплины группы). На занятии есть флаг `is_employee_assignment_overridden`.
- Слой `read/` для кросс-агрегатного чтения: проекция `XxxView` + `DbXxxView`, один SQL, скоуп по `ctx.orgId`/`ctx.branchId`, результат — тип из `api.schemas` напрямую, импорт `domain/**` запрещён. Образцы — `read/clients/ClientListView`, `read/groups/DbGroupListView` (скоуп по `g.branch_id = :branchId`), `read/home/DbTodayScheduleView` (занятия с именами группы и зала join'ами).
- Проекции занятий за период нет: неиспользуемый `GET /sessions/list` вместе с `read/sessions/` удалён в [#75](https://github.com/evtatarintsev/AthleticaCRM/pull/75). Занятия с названиями читает только `read/home/DbTodayScheduleView` — на один день, без фильтров и без скоупа по филиалу.
- Материализацией занятий владеет сверка `ScheduleSync` на записи (изменение `temporal-group-schedule`); чтение ничего не создаёт.
- Экран расписания существует как заглушка: `components/schedule/` с сеткой, ViewModel и локальным генератором данных `ScheduleStubData.kt`.

## Goals / Non-Goals

**Goals:**

- Один запрос на видимый период отдаёт всё, что нужно для отрисовки сетки, без обращения к справочникам.
- Фильтрация, скоуп по филиалу и подстановка названий выполняются одним SQL.
- Цвет резолвится в одном месте на сервере и переживает появление полей цвета без изменения контракта.

**Non-Goals:**

- Не трогаем материализацию занятий — это зона `ScheduleSync`.
- Не вводим пагинацию: границей объёма служит предельная длина периода.
- Не добавляем колонок цвета в БД: резолвер пишется сразу с тремя ступенями, но сегодня работает только последняя.
- Не отдаём признак переноса занятия ([#73](https://github.com/evtatarintsev/AthleticaCRM/issues/73)) и не сужаем фильтр тренеров до тренеров ([#74](https://github.com/evtatarintsev/AthleticaCRM/issues/74)).

## Decisions

### Проекция `read/schedule/`

Проекции группируются по экрану, поэтому расписание получает свой раздел: `read/schedule/ScheduleView.kt` (интерфейс + `ScheduleQuery`) и `DbScheduleView.kt`. Регистрируется в `ReadViews` как `schedule`.

```kotlin
interface ScheduleView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(query: ScheduleQuery): ScheduleListResponse
}

data class ScheduleQuery(
    val from: LocalDate,
    val to: LocalDate,
    val hallIds: List<HallId>?,          // null — фильтр не задан
    val disciplineIds: List<DisciplineId>?,
    val employeeIds: List<EmployeeId>?,
)
```

Нормализация — работа роута: пустой список фильтра превращается в `null`, чтобы проекция добавляла условие только для заданных фильтров.

### Один SQL: скоуп, фильтры и названия

```sql
SELECT s.id, s.group_id, g.name AS group_name, s.date, s.start_time, s.end_time, s.status,
       h.id AS hall_id, h.name AS hall_name,
       (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', e.id, 'name', e.name) ORDER BY e.name), '[]')
          FROM session_employees se JOIN employees e ON e.id = se.employee_id
         WHERE se.session_id = s.id) AS coaches,
       (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', d.id, 'name', d.name) ORDER BY d.name), '[]')
          FROM group_disciplines gd JOIN disciplines d ON d.id = gd.discipline_id
         WHERE gd.group_id = s.group_id) AS disciplines
FROM sessions s
JOIN groups g ON g.id = s.group_id AND g.branch_id = :branchId
JOIN halls  h ON h.id = s.hall_id
WHERE s.org_id = :orgId AND s.date BETWEEN :from AND :to
  [AND s.hall_id = ANY(:hallIds)]
  [AND EXISTS (SELECT 1 FROM session_employees se WHERE se.session_id = s.id AND se.employee_id = ANY(:employeeIds))]
  [AND EXISTS (SELECT 1 FROM group_disciplines gd WHERE gd.group_id = s.group_id AND gd.discipline_id = ANY(:disciplineIds))]
ORDER BY s.date, s.start_time, s.id
```

Точные имена колонок сотрудников и дисциплин уточняются по схеме при реализации; форма запроса — такая.

- **Филиал** — через `groups.branch_id` из `ctx.branchId`, как в `DbGroupListView`. Клиент филиал не передаёт — иначе появляется способ запросить чужой филиал в обход переключателя.
- **Занятия без группы** (`group_id IS NULL`) отсекаются самим `JOIN groups`: у них нет ни филиала, ни заголовка карточки. См. риски.
- **Фильтр по тренеру — по `session_employees`**, а не по `group_employees`: состав переопределяется на уровне занятия (`is_employee_assignment_overridden`). Занятие, где тренер группы A заменён на B, находится по B и не находится по A.
- **Внутри фильтра — «или»** (`= ANY`), **между фильтрами — «и»** (независимые условия `AND`).
- Индексы: `idx_sessions_org_date`, `idx_session_employees_employee`; по `groups.branch_id` при необходимости добавится отдельно.

### `POST /api/schedule/list` с телом

Три фильтра — списки идентификаторов, и выбор может быть большим. В query они разворачиваются в повторяющиеся параметры и упираются в длину URL. Прецедент чтения через POST с телом — `/clients/list`.

### Ответ денормализован

```kotlin
ScheduleListRequest(
    from: LocalDate, to: LocalDate,
    disciplineIds: List<DisciplineId> = emptyList(),
    hallIds: List<HallId> = emptyList(),
    employeeIds: List<EmployeeId> = emptyList(),
)

ScheduleListResponse(sessions: List<ScheduleSessionSchema>)

ScheduleSessionSchema(
    id: SessionId,
    group: ScheduleGroupSchema,                  // id + name
    date: LocalDate,
    startTime: LocalTime, endTime: LocalTime,    // продолжительность считает клиент
    hall: ScheduleHallSchema,                    // id + name
    coaches: List<ScheduleCoachSchema>,          // id + name, тренеры занятия
    disciplines: List<ScheduleDisciplineSchema>, // id + name, дисциплины группы
    status: SessionStatus,                       // перечисление, не строка
    colorKey: SessionColorKey,
)
```

Каждое занятие несёт названия вместе с идентификаторами, хотя фронт и так грузит каталоги для фильтров. Причины:

- Контракт самодостаточен: карточка рисуется из ответа без склейки с каталогами, ответ читается при отладке как есть.
- Каталог может не содержать сущность, которая осталась на прошлых занятиях (уволенный тренер, удалённая дисциплина) — склейка на клиенте дала бы пустую подпись.
- Код на фронте проще: нет `associateBy` и обработки промахов.

Отдельные маленькие схемы на каждое измерение (`ScheduleGroupSchema`, `ScheduleHallSchema`, `ScheduleCoachSchema`, `ScheduleDisciplineSchema`) вместо одной обобщённой пары id+name — чтобы идентификаторы остались типизированными (`HallId` ≠ `EmployeeId`). Приём уже применён в группах: `GroupEmployee`, `GroupDiscipline`, `GroupClient`.

Период (`from`/`to`) в ответе не повторяется: клиент знает, что запрашивал. Продолжительность не передаётся: это функция от `startTime`/`endTime`, а её формат — вопрос локали клиента. `isManual` и `isRescheduled` не передаются: спека их на карточке не требует, семантика переноса открыта в [#73](https://github.com/evtatarintsev/AthleticaCRM/issues/73).

### Фильтр тренеров — все сотрудники

Значения фильтра тренеров — весь каталог `employees.list`. Если сотрудник не тренирует, его выбор даёт понятный пустой результат; если же тренера, ведущего занятия, в фильтре нет — это выглядит как ошибка. Сужение до тренеров (роль, флаг или фактические назначения) — [#74](https://github.com/evtatarintsev/AthleticaCRM/issues/74); контракт `schedule/list` от него не зависит.

### Цвет: ключ палитры, резолв на сервере

Ответ несёт `colorKey` — значение перечисления (`ORANGE`, `PURPLE`, `STEEL`, `CYAN`, `GREEN`, `LIME`, `LILAC`, `GREY`), а не hex. Причины:

- Тёмная тема. Карточке нужна пара цветов (фон карточки и фон бейджа), и обе меняются между темами. Один hex с сервера этого не даёт.
- Контракт не привязан к оформлению: перекраска палитры не требует миграции данных.
- Будущий выбор цвета для дисциплины и группы становится выбором из набора, как в календарных продуктах, а не произвольной пипеткой.

Правило: цвет группы → цвет первой дисциплины → запасной. Сегодня полей цвета нет, поэтому ключ считается в маппере строки `DbScheduleView` чистой функцией `colorKeyFor(groupId, firstDisciplineId)` — это третья ступень. Когда колонки появятся, первые две ступени станут `COALESCE(g.color, d.color)` в том же SQL, а функция останется запасным вариантом для `NULL`. Контракт не меняется.

Запасной ключ выводится из младших битов UUID (первой дисциплины, а при её отсутствии — группы) по модулю размера палитры. Именно из битов, а не через `hashCode()`: ключ должен быть одинаковым между рантаймами и версиями, иначе карточки перекрашиваются на ровном месте. Выбор по дисциплине, а не по занятию, даёт требуемое спекой свойство «одинаковая дисциплина — одинаковый цвет». «Первая дисциплина» — первая в порядке, которым её отдаёт SQL, поэтому порядок агрегата должен быть детерминированным.

Неизвестный клиенту ключ (сервер новее клиента) рисуется нейтральным — обычный forward compatibility.

### Ограничение периода — 62 дня

Проверка в роуте до вызова проекции: `from <= to` и длина периода не больше 62 дней включительно (`from.daysUntil(to) + 1 <= 62`), иначе `CommonDomainError` с кодами `INVALID_SCHEDULE_PERIOD` / `SCHEDULE_PERIOD_TOO_LONG` и локализованными сообщениями из `Messages`. 62 дня покрывают месячную сетку с выравниванием по неделям (до 42 дней) с запасом, но не дают запросить год одним вызовом. Ограничение удерживает объём выборки: записи при чтении нет вовсе.

### Материализации при чтении нет

Занятия создаёт сверка `ScheduleSync` в транзакции изменения расписания и ежедневным тиком, горизонт зажат внутри неё. Проекция только читает. Занятия за горизонтом материализации в выдачу не попадают — что показывать за его границей, решается отдельно.

### Группировка по дням и часам — на клиенте

Сервер отдаёт плоский список занятий периода. Раскладка по колонкам-дням и строкам-часам зависит от режима отображения (неделя сейчас, день и месяц позже) и меняется без участия бэкенда.

### Фронтенд

`ScheduleStubData.kt` удаляется. Состояние страницы остаётся одной ячейкой: `ScheduleState(weekStart, data: ScheduleLoadState, filters, dictionaries)`, где `ScheduleLoadState` — `Loading | Loaded | Error` по образцу `HomeLoadState`. Фильтры и справочники живут в том же снимке, чтобы переключение недели и смена фильтров не расходились. Справочники нужны только для панели фильтров — карточки их не используют.

`ScheduleEventColor` перестаёт быть источником правды и становится отображением `SessionColorKey` в пару цветов темы.

```
   открытие страницы                  переключение недели / фильтра
          |                                        |
          v                                        v
  halls.list                           POST schedule/list(from,to,filters)
  disciplines.list   --> панель                    |
  employees.list         фильтров                  v
                                      DbScheduleView: один SQL
                                                   |
                                                   v
                                     плоский денормализованный список
                                                   |
                                       группировка по дням/часам
                                                   |
                                                   v
                                              сетка недели
```

## Risks / Trade-offs

- **Занятия за горизонтом материализации не видны.** Листание недель вперёд однажды упрётся в границу горизонта, за которой занятий просто нет. → Граница введена изменением `temporal-group-schedule`; что показывать за ней — отдельный вопрос.
- **Занятия без группы исчезают из расписания.** `group_id` обнуляется при удалении группы. → Сейчас пользователь всё равно не увидел бы у такой карточки ни названия, ни филиала. Если группы удаляются в реальной эксплуатации, правильное лечение — запретить удаление группы с занятиями, а не показывать безымянные карточки.
- **Нет пагинации.** На пределе периода и крупном филиале ответ может вырасти до тысяч занятий, а денормализация увеличивает каждый элемент. → Недельный режим держит объём в десятках-сотнях; предел периода — жёсткая верхняя граница. Замерить на реальном объёме до включения режима «месяц».
- **Коррелированные подзапросы на каждое занятие.** Агрегаты тренеров и дисциплин считаются на строку. → На объёме недели это десятки-сотни строк; при росте — переписать на `LEFT JOIN LATERAL` или предагрегацию без изменения контракта.
- **Фильтр тренеров засорён нетренерами.** → Осознанно до решения [#74](https://github.com/evtatarintsev/AthleticaCRM/issues/74).
- **Фиксированная палитра.** Когда появится выбор цвета для группы, он будет ограничен набором ключей. → Это осознанный обмен на работающие темы; расширять палитру дешевле, чем чинить контраст произвольного hex в тёмной теме.
- **Фильтр по дисциплинам через группу.** Дисциплины принадлежат группе, а не занятию, поэтому смена дисциплин группы задним числом меняет фильтрацию уже прошедших занятий. → Соответствует текущей модели; фиксация дисциплин на занятии — отдельное решение, если оно понадобится.

## Migration Plan

Миграций БД и новых переменных окружения нет. Изменение аддитивно: новая проекция, новый роут и новые схемы; существующие endpoint-ы не трогаются.

Порядок: контракт в `shared` → проекция `read/schedule/` и роут → API-клиент → переключение страницы с заглушки. До последнего шага страница продолжает показывать заглушку и не ломается.

Откат — возврат коммита: внешних потребителей у нового endpoint-а нет, данные он не меняет вовсе.

## Open Questions

- Состав и конкретные цвета палитры: восемь ключей взяты из текущей заглушки экрана. Уточняется при появлении выбора цвета и на подход не влияет.
- Пометка перенесённых занятий — [#73](https://github.com/evtatarintsev/AthleticaCRM/issues/73).
- Какие сотрудники попадают в фильтр тренеров — [#74](https://github.com/evtatarintsev/AthleticaCRM/issues/74).
