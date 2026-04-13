# Быстрая справка по юзкейсам ядра CRM

## 🎯 4 Модуля ядра: Что реализовать

### 1️⃣ КЛИЕНТСКАЯ БАЗА

| UC | Название | Описание | Статус | Приоритет |
|----|----------|---------|--------|-----------|
| 1.1.1 | ListClients с фильтрацией | Список + сортировка по имени, дате, балансу | ⚠️ Частично | Высокий |
| 1.1.2 | SearchClients | Быстрый поиск по имени/контактам | ❌ | Средний |
| 1.2.1 | UpdateClientInfo | Редактирование имени, даты рождения, пола | ❌ | Высокий |
| 1.2.2 | UpdateClientContacts | Несколько номеров/email | ❌ | Средний |
| 1.2.4 | AddClientNote | Внутренние заметки | ❌ | Средний |
| 1.2.5 | MarkClientAsVIP | Пометить VIP | ❌ | Низкий |
| 1.3.1 | ClientActivityTimeline | История: посещения + платежи + изменения | ❌ | Высокий |
| 1.5.1 | ArchiveClient | Мягкое удаление | ❌ | Средний |

---

### 2️⃣ АБОНЕМЕНТЫ & ПЛАНЫ

| UC | Название | Описание | Статус | Приоритет |
|----|----------|---------|--------|-----------|
| 2.1.1 | CreateMembershipPlan | Создать план абонемента (цена, период, кол-во занятий) | ❌ | **ВЫСОКИЙ** |
| 2.1.5 | ListMembershipPlans | Список активных и архивированных планов абонементов | ❌ | **ВЫСОКИЙ** |
| 2.1.3 | UpdateMembershipPlan | Редактирование (только если никто не подписан) | ❌ | Средний |
| 2.1.4 | ArchiveMembershipPlan | Выключение продаж | ❌ | Средний |
| 2.3.1 | CreateMembership | **[КРИТИЧНО]** Продажа абонемента клиенту | ❌ | **ВЫСОКИЙ** |
| 2.3.2 | ListClientMemberships | Активные абонементы клиента | ❌ | **ВЫСОКИЙ** |
| 2.3.3 | MembershipDetail | Детали (баланс занятий, дата истечения) | ❌ | **ВЫСОКИЙ** |
| 2.3.4 | RenewMembership | Продление абонемента | ❌ | Средний |
| 2.3.5 | CancelMembership | Отмена и возврат (Phase 2) | ❌ | Низкий |
| 2.4.1 | DeductMembership | Убыль занятия при посещении | ❌ | **ВЫСОКИЙ** |
| 2.4.2 | RestoreMembership | Восстановление занятия при отмене | ❌ | **ВЫСОКИЙ** |
| 2.4.4 | MembershipLowBalanceAlert | Уведомление при остатке ≤2 | ❌ | Средний |
| 2.5.1 | RecordPayment | Регистрация оплаты | ❌ | **ВЫСОКИЙ** |

---

### 3️⃣ ПОСЕЩАЕМОСТЬ & УЧЕТ ЗАНЯТИЙ

| UC | Название | Описание | Статус | Приоритет |
|----|----------|---------|--------|-----------|
| 3.1.1 | CheckinClient | **[КРИТИЧНО]** Регистрация посещения (REGISTERED статус) | ❌ | **ВЫСОКИЙ** |
| 3.1.4 | RemoveAttendance | Удаление чернови́ка (если ошибка) | ❌ | **ВЫСОКИЙ** |
| 3.1.5 | CancelAttendance | Отмена финализированного посещения (с восстановлением) | ❌ | **ВЫСОКИЙ** |
| 3.1.3 | TransferCheckin | Перемещение посещения между занятиями | ❌ | Средний |
| 3.2.1 | SessionAttendanceList | Список участников перед занятием | ❌ | **ВЫСОКИЙ** |
| 3.2.3 | ClientAttendanceHistory | История посещений конкретного клиента | ❌ | Средний |
| 3.2.4 | GroupAttendanceReport | Отчет по посещаемости группы | ❌ | Средний |
| 3.3.1 | ClientActivityRating | Рейтинг активности | ❌ | Низкий (Phase 2) |
| 3.3.2 | ChurnAnalysis | Анализ: кто перестал ходить | ❌ | Низкий (Phase 2) |

---

### 4️⃣ РАСПИСАНИЕ ЗАНЯТИЙ

| UC | Название | Описание | Статус | Приоритет |
|----|----------|---------|--------|-----------|
| 4.1.1 | CreateGroup + Schedule | Создание группы с расписанием | ✅ Есть | ✅ Готово |
| 4.1.2 | UpdateGroupSchedule | Изменение слотов расписания | ❌ | Средний |
| 4.1.4 | OrgScheduleCalendar | Полное расписание организации | ❌ | Средний |
| 4.2.1 | GenerateSessions | **[КРИТИЧНО]** Генерирование реальных занятий из расписания | ❌ | **ВЫСОКИЙ** |
| 4.2.4 | AssignTrainerToSession | Назначение тренера на занятие | ❌ | **ВЫСОКИЙ** |
| 4.2.6 | CompleteSession | **[КРИТИЧНО]** Завершение занятия (DeductMembership + ATTENDED) | ❌ | **ВЫСОКИЙ** |
| 4.2.2 | CancelSession | Отмена одного занятия (все Attendance → CANCELLED) | ❌ | Средний |
| 4.2.3 | RescheduleSession | Перенос занятия на другой день/время | ❌ | Средний |
| 4.2.5 | TrainerSchedule | Расписание тренера | ❌ | Средний |
| 4.3.1 | CreateRoom | Создание помещения | ❌ | **ВЫСОКИЙ** |
| 4.3.2 | AssignGroupToRoom | Привязка группы к залу | ❌ | **ВЫСОКИЙ** |
| 4.3.3 | RoomAvailability | Проверка конфликтов (две группы в один зал) | ❌ | Средний |

---

## 📊 MVP Phase 1: Минимум что нужно (10 UC)

```
1. CreateMembershipPlan              ← Администратор создаёт планы абонементов
2. CreateMembership          ← Администратор продаёт абонемент
3. RecordPayment             ← Администратор регистрирует оплату
4. GenerateSessions    ← Система генерирует занятия (background job)
5. AssignTrainerToSession   ← Тренер/админ назначает себя на занятие
6. CheckinClient             ← Тренер отмечает посещение (REGISTERED черновик)
7. CompleteSession   ← Тренер завершает занятие (DeductMembership + ATTENDED)
8. RemoveAttendance / CancelAttendance ← Отмена посещения
9. CreateRoom                ← Администратор создаёт залы
10. AssignGroupToRoom        ← Администратор привязывает группы к залам
```

**Дополнительно (quick wins):**
- ListMembershipPlans
- ListClientMemberships
- MembershipDetail
- SessionAttendanceList
- GroupScheduleCalendar

---

## 🔄 Критичные потоки данных

### Поток A: Покупка абонемента
```
Admin выбирает MembershipPlan
    ↓
Admin выбирает Client
    ↓
CreateMembership (status=pending)
    ↓
RecordPayment
    ↓
Membership.status = active
Membership.balance = plan.sessions_count
Membership.expires_at = now + plan.period_days
```

### Поток B: Посещение занятия (ОТЛОЖЕННОЕ СПИСАНИЕ) 🔄

⚠️ **НОВЫЙ ПАТТЕРН** — см. [`ATTENDANCE_DEDUCTION_PATTERNS.md`](./ATTENDANCE_DEDUCTION_PATTERNS.md)

```
ЭТАП 1: РЕГИСТРАЦИЯ
├─ Тренер открывает Session (status = SCHEDULED)
├─ Система показывает список Client-ов
├─ Тренер нажимает CheckinClient
├─ Attendance created (status = REGISTERED)
└─ ✅ Membership.balance НЕ МЕНЯЕТСЯ ← Ключевое отличие!

ЭТАП 2: ЗАВЕРШЕНИЕ ЗАНЯТИЯ
├─ Тренер нажимает "Завершить занятие"
├─ CompleteSession(sessionId)
├─ Session.status = COMPLETED
├─ TRANSACTION: Для каждого REGISTERED:
│  ├─ DeductMembership (Membership.balance -= 1)
│  ├─ Attendance.status = ATTENDED
│  └─ Если balance == 0 → Membership.status = exhausted
│     └─ Async: уведомления, задачи менеджеру
└─ Транзакция: "всё или ничего"
```

**Преимущества:**
- ✅ Откат ошибки = просто `RemoveAttendance` (без каскадных откатов)
- ✅ Нет race conditions (всё в одной транзакции)
- ✅ Асинхронные эффекты только финальные, точные

### Поток C: Отмена посещения

```
ВАРИАНТ C1: Отмена черновика (до завершения)
├─ RemoveAttendance(attendanceId)
└─ ✅ Никаких откатов (баланс не менялся)

ВАРИАНТ C2: Отмена состоявшегося (после завершения)
├─ CancelAttendance(attendanceId)
├─ Membership.balance += 1 (восстанавливаем)
├─ Если был exhausted → active
└─ Async: уведомление об отмене
```

### Поток D: Истечение абонемента
```
Фоновый процесс (каждый день 00:00)
    ↓
Найти все Membership где expires_at < now
    ↓
Membership.status = expired
    ↓
[Phase 2: Отправить уведомление клиенту]
```

---

## 📈 Как это работает в UI

### Сценарий 1: Администратор создаёт план абонемента
```
Admin → CRM → Планы абонементов → Создать
  Input: Название, цена, период (дней), количество занятий, дисциплины
  Output: План абонемента создан, виден в списке
```

### Сценарий 2: Рецепционист продаёт абонемент
```
Клиент приходит → Рецепционист → Клиент (search/select)
  Input: Выбрать план абонемента, способ оплаты, сумму
  Output: Абонемент активен, баланс виден в профиле клиента
```

### Сценарий 3: Тренер отмечает посещение
```
Занятие начинается → Тренер открывает Session
  Видит: Список клиентов которые зарегистрированы (have active membership)
  Действие: Нажимает имя → Attendance created → Membership.balance -= 1
  Видит: Обновленный баланс клиента (e.g. 15 → 14 занятий)
```

### Сценарий 4: Планирование расписания
```
Админ → Группа "Йога ПН/СР" → GenerateSessions (за месяц)
  Система создаёт: 8 занятий (4 ПН + 4 СР)
  Видит: Расписание на месяц в виде календаря
  Действие: Назначить тренера на каждое занятие
```

---

## 🛠️ Технические детали для разработчика

### Таблицы (новые)
```sql
membership-plans (id, org_id, name, type, price, period_days, sessions_count)
memberships (id, client_id, plan_id, status, balance, expires_at, created_at, paid_at)
sessions (id, group_id, scheduled_at, room_id, trainer_id, status)
attendance (id, client_id, session_id, membership_id, attended_at, status)
rooms (id, org_id, name, capacity)
```

### Enum Status
```kotlin
enum class MembershipStatus { PENDING_PAYMENT, ACTIVE, EXPIRED, EXHAUSTED, CANCELLED }
enum class AttendanceStatus { ATTENDED, NO_SHOW, CANCELLED }
enum class SessionStatus { SCHEDULED, COMPLETED, CANCELLED }
enum class MembershipPlanType { CLASSIC, UNLIMITED, DROPIN, PACKAGE }
```

### Фоновые процессы (Schedulers)
```kotlin
// Каждый день 00:00 UTC
ExpireMembershipsScheduler

// Каждый день (генерирование на неделю вперед)
GenerateSessionsScheduler
```

### API Endpoints (примеры)
```
POST   /api/membership-plans
GET    /api/membership-plans
GET    /api/membership-plans/{id}
PUT    /api/membership-plans/{id}

POST   /api/memberships
GET    /api/clients/{clientId}/memberships
GET    /api/memberships/{id}

POST   /api/instances/{sessionId}/attendance
DELETE /api/attendance/{id}

POST   /api/rooms
GET    /api/rooms

POST   /api/groups/{groupId}/instances/generate
```

---

## ✅ Checklist перед началом разработки

- [ ] Обсудить 5 вопросов для обсуждения (в PLANNING_SUMMARY.md)
- [ ] Создать миграции БД (Flyway scripts)
- [ ] Создать Kotlin data classes для Request/Response
- [ ] Создать Enum для Status полей
- [ ] Создать Error codes (e.g., PLAN_*, MEMBERSHIP_*)
- [ ] Создать контрактные тесты для API
- [ ] Настроить fixtures для интеграционных тестов
- [ ] Создать OpenAPI спецификацию
- [ ] Выбрать инструмент для Background Jobs (e.g., Quartz, Spring Scheduler)
- [ ] Выбрать инструмент для Notifications (e.g., WebSocket, Firebase, Kafka)

---

## 📚 Документы для углубленного изучения

1. **USECASES_CORE.md** — Полное описание каждого юзкейса с деталями
2. **ARCHITECTURE_CORE.md** — Архитектура, модель данных, граничные случаи
3. **PLANNING_SUMMARY.md** — Timeline, критичные решения, вопросы

---

## 💡 Tips

- **Начните с MembershipPlan + Membership** — это фундамент всего остального
- **Тесты пишите параллельно** — не откладывайте на конец
- **Используйте transactions** для операций DeductMembership (конкуренция)
- **Логируйте всё** в audit_log для отладки и compliance
- **Документируйте API** по мере разработки (OpenAPI)
- **Код ревью** перед мержом (особенно для бизнес-логики)

