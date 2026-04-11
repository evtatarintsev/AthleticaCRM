# Архитектура ядра CRM

## 1. Основные сущности и их взаимосвязи

```
┌─────────────────────────────────────────────────────────────┐
│                    ОРГАНИЗАЦИЯ (Org)                        │
│  (одна спортивная организация может быть мультифилиальной)  │
└──────────────────────────────────────────┬──────────────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
                    ▼                      ▼                      ▼
          ┌──────────────────┐    ┌────────────────┐    ┌────────────────┐
          │    CLIENT        │    │    ROOM        │    │    GROUP       │
          ├──────────────────┤    ├────────────────┤    ├────────────────┤
          │ id               │    │ id             │    │ id             │
          │ name             │    │ name           │    │ name           │
          │ contacts         │    │ capacity       │    │ discipline_ids │
          │ avatar_id        │    │ equipment      │    │ room_id        │
          │ birthday         │    └────────────────┘    │ trainer_id     │
          │ gender           │                          │ status         │
          │ status (active)  │                          └────────────────┘
          │ notes            │                                  │
          │ balance (total)  │                                  │
          └────────┬─────────┘                                  │
                   │                                            │
                   │                                            │
                   ▼                                            ▼
        ┌──────────────────────┐                  ┌─────────────────────┐
        │    MEMBERSHIP        │                  │   SCHEDULE_SLOT     │
        │  (абонемент)         │                  │   (слот расписания)  │
        ├──────────────────────┤                  ├─────────────────────┤
        │ id                   │                  │ group_id            │
        │ client_id            │                  │ day_of_week         │
        │ tariff_id            │                  │ start_time          │
        │ status               │                  │ end_time            │
        │ created_at           │                  │ recurrence (if any) │
        │ expires_at           │                  └─────────────────────┘
        │ balance (занятий)    │                           │
        │ paid_at              │                           │
        │ price                │                           │
        └────────┬─────────────┘                           │
                 │                                         │
                 │                         ┌───────────────┘
                 │                         │
                 ▼                         ▼
        ┌──────────────────────┐    ┌──────────────────────┐
        │   ATTENDANCE         │    │  GROUP_INSTANCE      │
        │  (посещение)         │    │  (реальное занятие)  │
        ├──────────────────────┤    ├──────────────────────┤
        │ id                   │    │ id                   │
        │ client_id            │    │ group_id             │
        │ instance_id          │    │ scheduled_at         │
        │ attended_at          │    │ room_id              │
        │ membership_id        │    │ trainer_id           │
        │ status (attended)    │    │ actual_start_time    │
        │ notes                │    │ actual_end_time      │
        └──────────────────────┘    │ status (scheduled)   │
                                    │ notes                │
                                    └──────────────────────┘

┌──────────────────────┐    ┌──────────────────────┐
│    TARIFF            │    │    DISCIPLINE        │
│  (тарифный план)     │    │  (спортивная дис.)   │
├──────────────────────┤    ├──────────────────────┤
│ id                   │    │ id                   │
│ name                 │    │ name                 │
│ description          │    │ description          │
│ type (classic,       │    │ org_id               │
│      unlimited,      │    │ status               │
│      dropin,         │    └──────────────────────┘
│      package)        │
│ price                │
│ period_days          │
│ lessons_count        │
│ discipline_ids       │
│ status (active)      │
└──────────────────────┘
```

## 2. Критические потоки данных

### Поток A: Покупка абонемента
```
1. Admin выбирает Tariff
2. Admin выбирает Client
3. Система создаёт Membership (status = pending_payment)
4. Admin регистрирует платёж
5. Membership → status = active, balance = tariff.lessons_count
6. Membership → expires_at = now + tariff.period_days
```

### Поток B: Посещение занятия (ОТЛОЖЕННОЕ СПИСАНИЕ) 🔄

⚠️ **РЕКОМЕНДУЕМЫЙ ПОДХОД:** Списание происходит НЕ при CheckinClient, а при MarkInstanceAsCompleted. Это избегает проблем с откатами и каскадными эффектами. [Подробный анализ в `ATTENDANCE_DEDUCTION_PATTERNS.md`](./ATTENDANCE_DEDUCTION_PATTERNS.md)

```
ЭТАП 1: РЕГИСТРАЦИЯ (в начале занятия)
├─ 1. Тренер открывает GroupInstance (status = SCHEDULED)
├─ 2. Система показывает список Client-ов в Group
├─ 3. Тренер отмечает посещение → CheckinClient(clientId)
│  └─ Attendance created с status = REGISTERED (черновик!)
│  └─ ✅ Membership.balance НЕ МЕНЯЕТСЯ ← Ключевое отличие!
│  └─ Риск откатов: МИНИМАЛЕН (просто delete Attendance)

ЭТАП 2: ЗАВЕРШЕНИЕ ЗАНЯТИЯ (в конце)
└─ 4. Тренер нажимает "Завершить занятие" → MarkInstanceAsCompleted(instanceId)
   ├─ GroupInstance.status = COMPLETED
   ├─ TRANSACTION: Для каждого REGISTERED Attendance:
   │  ├─ Membership.balance -= 1 ← Списание (атомарно!)
   │  ├─ Attendance.status = ATTENDED ← Финализация
   │  └─ Если balance == 0 → Membership.status = EXHAUSTED
   │     └─ 🟢 ASYNC EVENTS: уведомления, задачи менеджеру (один раз, точно)
   └─ Транзакция гарантирует: либо всё, либо ничего
```

**Почему этот подход лучше:**
- ✅ Откат ошибки = простое `RemoveAttendance` (без каскадных откатов)
- ✅ Нет race conditions с balance (всё в одной транзакции)
- ✅ Асинхронные эффекты (notifications, tasks) только финальные, точные
- ✅ Легко откатить целое занятие (просто `CancelInstance` → все Attendance = CANCELLED)
- ❌ Но требует TWO операций (CheckIn + Complete) вместо одной

### Поток C: Отмена посещения

```
ВАРИАНТ C1: Отмена черновика (до завершения занятия)
├─ 1. RemoveAttendance(attendanceId)
└─ 2. Attendance удалена из БД
   └─ Membership.balance не менялась → ничего не откатываем
   └─ Чисто и просто! ✓

ВАРИАНТ C2: Отмена уже состоявшегося посещения (после завершения)
├─ 1. CancelAttendance(attendanceId) [Attendance.status = ATTENDED]
├─ 2. Membership.balance += 1 (восстанавливаем)
├─ 3. Если был EXHAUSTED → Membership.status = ACTIVE
└─ 4. Attendance.status = CANCELLED
   └─ Асинхронно: уведомить клиента об отмене
```

### Поток D: Истечение абонемента
```
1. Фоновый процесс проверяет каждый день
2. Если expires_at < now → Membership.status = expired
3. Клиенту отправляется уведомление (когда будет маркетинг)
```

## 3. Важные бизнес-правила

### Абонементы
- Один клиент может иметь несколько активных абонементов одновременно (разные дисциплины)
- Абонемент считается активным если: status = active И expires_at > now И balance > 0
- При посещении используется ПЕРВЫЙ подходящий активный абонемент (по дате создания или приоритету)
- Истекший абонемент нельзя возобновить (нужна новая продажа)

### Посещение
- Клиент может посетить занятие ТОЛЬКО если у него есть активный абонемент ДЛЯ ЭТОЙ группы/дисциплины
- Исключение: drop-in абонемент (день-в-день) работает для любой группы
- День-в-день должны автоматически списываться с баланса клиента (через AdjustClientBalance)
- Одно посещение = одно занятие (никакой частичной оплаты)

### Расписание
- GroupInstance генерируется из ScheduleSlot (расписание группы)
- Если ScheduleSlot: ПН, СР, ПТ → каждый месяц создаются 12 instances
- Если занятие отменено (болезнь тренера) → GroupInstance.status = cancelled
- Клиенты уведомляются об отмене (когда будет)

### Залы
- GroupInstance должна иметь назначенный Room
- Нельзя создать два GroupInstance в один Room в одно время (проверка конфликтов)
- Комната может быть не назначена (занятие может быть online или на улице) → room_id = NULL

## 4. Переходные состояния (State Machines)

### Membership (абонемент)
```
PENDING_PAYMENT
    ↓ (оплачено)
ACTIVE
    ├─ (истекла дата) → EXPIRED
    ├─ (кончились занятия) → EXHAUSTED
    └─ (пользователь отменил) → CANCELLED
EXPIRED / EXHAUSTED / CANCELLED
    ↓ (новая покупка или восстановление)
ACTIVE
```

### GroupInstance (занятие)
```
SCHEDULED
    ├─ (клиент посетил) → был Attendance created
    ├─ (отменено) → CANCELLED
    └─ (время наступило) → COMPLETED
COMPLETED / CANCELLED
    └─ (архив - не менять)
```

### Attendance (посещение)
```
REGISTERED / ATTENDED
    ├─ (отменить посещение) → CANCELLED
    └─ (автоматически при истечении дня)

CANCELLED / NO_SHOW
    └─ (архив)
```

## 5. Технические требования

### Индексы (для 1000 клиентов это не критично, но нужны)
```sql
-- Быстрый поиск по org_id (мультитенантность)
CREATE INDEX idx_clients_org_id ON clients(org_id);
CREATE INDEX idx_memberships_org_id ON memberships(org_id);
CREATE INDEX idx_group_instances_org_id ON group_instances(org_id);

-- Быстрый поиск активных абонементов
CREATE INDEX idx_memberships_client_status ON memberships(client_id, status);

-- Поиск занятий по дате (расписание)
CREATE INDEX idx_group_instances_scheduled ON group_instances(scheduled_at);

-- Поиск посещений
CREATE INDEX idx_attendance_instance ON attendance(instance_id);
CREATE INDEX idx_attendance_client_date ON attendance(client_id, attended_at);
```

### Обработка ошибок (рассы конкуренции)
- **Проблема:** Двое admin-ов одновременно отмечают посещение у одного клиента с одним абонементом
- **Решение:** Использовать транзакции с уровнем изоляции REPEATABLE_READ, pessimistic lock на Membership.balance
- **Или:** Использовать версионирование (version field) и optimistic locking

### Фоновые процессы (scheduler)
```kotlin
// Каждый день в 00:00 UTC
ExpireMembershipsScheduler {
    UPDATE memberships 
    SET status = 'expired' 
    WHERE org_id = ?
      AND expires_at < now()
      AND status = 'active'
}

// Ежедневно генерирование GroupInstances на неделю вперед
GenerateGroupInstancesScheduler {
    Для каждой Group найти ScheduleSlots
    Для каждого ScheduleSlot создать GroupInstance на предстоящей неделе
    (это нужно для отметки посещений тренерами)
}
```

## 6. API слой (примеры)

### Покупка абонемента
```
POST /api/memberships
{
  "clientId": "uuid",
  "tariffId": "uuid",
  "paymentMethod": "cash" | "card" | "transfer",
  "paidAmount": 5000.00
}

Response:
{
  "id": "uuid",
  "clientId": "uuid",
  "tariffId": "uuid",
  "status": "active",
  "balance": 16,           // количество занятий
  "expiresAt": "2026-05-11T00:00:00Z",
  "createdAt": "2026-04-11T12:00:00Z"
}
```

### Регистрация посещения
```
POST /api/instances/{instanceId}/attendance
{
  "clientId": "uuid"
}

Response (на успех):
{
  "id": "uuid",
  "instanceId": "uuid",
  "clientId": "uuid",
  "attendedAt": "2026-04-11T18:30:00Z",
  "membershipId": "uuid",
  "membershipBalance": 15  // осталось занятий
}

Response (ошибка):
{
  "code": "NO_ACTIVE_MEMBERSHIP",
  "message": "У клиента нет активного абонемента для этой дисциплины"
}
```

### Просмотр занятия с участниками
```
GET /api/instances/{instanceId}

Response:
{
  "id": "uuid",
  "groupId": "uuid",
  "groupName": "Йога уровень 1",
  "scheduledAt": "2026-04-14T18:00:00Z",
  "room": {
    "id": "uuid",
    "name": "Зал А"
  },
  "trainer": {
    "id": "uuid",
    "name": "Мария"
  },
  "participants": [
    {
      "clientId": "uuid",
      "name": "Иван",
      "status": "registered",  // registered | attended | cancelled | no_show
      "membershipId": "uuid",
      "membershipBalance": 14
    }
  ],
  "status": "scheduled"  // scheduled | completed | cancelled
}
```

## 7. Интеграционные точки с другими модулями

### С модулем Сотрудников
- Тренер должен быть создан в Employees
- GroupInstance должна иметь trainer_id (ссылку на Employee)
- Нужна проверка: можно ли назначить тренера на это время (конфликты расписания)

### С модулем Финансов (Phase 2)
- Платёж за Membership должен создавать запись в финансовом журнале
- Возврат абонемента → возврат денег

### С модулем Задач (Phase 2)
- Если клиент перестал ходить (churn) → создать Task для связи с ним

### С модулем Маркетинга (Phase 2)
- Уведомления при истечении абонемента
- Рассылки новых промо-тарифов
- SMS/email напоминания перед занятиями

## 8. Граничные случаи (edge cases)

### Случай 1: Двойное посещение
**Проблема:** Система позволила отметить одного клиента на два разных занятия в один момент времени.
**Решение:** При создании Attendance проверить: нет ли у этого клиента уже посещения в этот момент (GROUP BY client_id, attended_at с интервалом ±30 мин)

### Случай 2: Истечение баланса абонемента
**Проблема:** Клиент посетил последнее занятие, балан стал 0. Должен ли он видеть эту группу в мобильном приложении?
**Решение:** Нет, показывать только группы с активными абонементами (status = active AND balance > 0)

### Случай 3: Переход между часовыми поясами
**Проблема:** Организация в Москве, но используется UTC в БД. Расписание "18:00 по Москве" должно отображаться корректно.
**Решение:** Хранить timezone_id в Org settings, все times в БД в UTC, конвертировать при отображении

### Случай 4: Отмена группы
**Проблема:** Администратор хочет отменить всю группу (например, "Йога по вторникам"). Что с активными абонементами?
**Решение:**
- Сценарий 1: Мягкое удаление (Group.status = inactive) - абонементы остаются, но новые посещения невозможны
- Сценарий 2: Возврат денег за оставшиеся занятия - требует UI и интеграции с финансами
- Рекомендация: MVP - только мягкое удаление

---

## 9. Checklist для реализации каждого юзкейса

При разработке каждого UC убедиться:

- [ ] Правильно проверяется orgId (мультитенантность)
- [ ] Есть все необходимые валидации (dates, amounts, etc)
- [ ] Ошибки обработаны и локализованы
- [ ] Есть unit тесты для бизнес-логики
- [ ] Есть e2e тесты для критических потоков (A, B, C, D выше)
- [ ] Логирование в audit_log
- [ ] Правильно обновлены индексы
- [ ] Нет N+1 queries (JOINы правильные)
- [ ] Транзакции правильно вложены (если нужны)
- [ ] Результат возвращается в правильном формате (API schema)
- [ ] Документирован в API (OpenAPI/Swagger)

