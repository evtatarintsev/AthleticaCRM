# Паттерны списания абонемента при посещении

**Версия:** 1.0  
**Статус:** Анализ и рекомендация  
**Дата:** 2026-04-11

---

## 📌 Проблема

Когда убывать занятие из абонемента?

- **Вариант A (текущий план):** Сразу при CheckinClient (отметке посещения)
- **Вариант B (альтернативный):** Отложенно, после MarkInstanceAsCompleted (когда тренер отмечает что занятие проведено)

---

## 🔄 Вариант A: Немедленное списание при CheckinClient

### Логика потока

```
1. Тренер открывает GroupInstance
2. Тренер видит список клиентов с активными Membership
3. Тренер нажимает "Отметить посещение" → CheckinClient(clientId)
   
   Действия системы:
   ├─ Attendance created (status = ATTENDED)
   ├─ Membership.balance -= 1
   ├─ Если balance == 0 → Membership.status = EXHAUSTED
   │   └─ 🔴 TRIGGER: запускаются процессы (уведомление, задача менеджеру, etc)
   └─ API возвращает успех

4. Если ошибка (отметили не того клиента):
   └─ CancelCheckin → Attendance.status = CANCELLED
      ├─ Membership.balance += 1
      ├─ Если был EXHAUSTED → Membership.status = ACTIVE
      │   └─ 🔴 PROBLEM: откатываем процессы??? 
      └─ Сложно!
```

### Проблемы этого подхода

#### Проблема 1: Каскадные откаты
```kotlin
// Сценарий: У клиента последнее занятие
CheckinClient("ivanov") // balance было 1
  → balance = 0
  → status = EXHAUSTED
  → [ASYNC] notifyClient("ivanov") ← отправлена уведомление
  → [ASYNC] createTask(manager_id, "Перезвонить Иванова")
  → [ASYNC] addToChurnList("ivanov") ← добавлен в список неактивных

// Тренер говорит: "Стоп, это ошибка, я отметил не того"
CancelCheckin(attendanceId)
  → balance = 1
  → status = ACTIVE
  → ??? Откатить уведомление?
  → ??? Удалить задачу менеджеру?
  → ??? Удалить из churnList?
```

**Результат:** Асинхронные эффекты (notifications, tasks) создают рассогласованность. Нужен механизм отката.

#### Проблема 2: Race condition при последнем занятии
```kotlin
// Два тренера одновременно отмечают одно и то же занятие
Thread 1: CheckinClient("ivanov")  // balance 1 → 0, exhausted ✓
Thread 2: CheckinClient("ivanov")  // balance 0 → -1 ✗ NEGATIVE BALANCE!
```

**Решение:** Pessimistic lock на Membership, но это замедляет операцию.

#### Проблема 3: Ошибки в отметке посещения
```
Тренер случайно нажал на клиента два раза:
  1. CheckinClient("ivanov") → balance 10 → 9 ✓
  2. CheckinClient("ivanov") → balance 9 → 8 ✓
  
Результат: Клиент потерял 2 занятия вместо 1
Решение: Нужна проверка "уже отмечено", но усложняет логику
```

---

## ✅ Вариант B: Отложенное списание при MarkInstanceAsCompleted

### Логика потока

```
1. Тренер открывает GroupInstance (status = SCHEDULED)
2. Тренер видит список клиентов
3. Тренер нажимает "Отметить посещение" → CheckinClient(clientId)
   
   Действия системы:
   ├─ Attendance created (status = REGISTERED)
   ├─ Membership NOT CHANGED (balance тот же)
   └─ API возвращает успех (быстро!)

4. После занятия (в конце дня или явно) → MarkInstanceAsCompleted(instanceId)
   
   Действия системы:
   ├─ GroupInstance.status = COMPLETED
   ├─ Для каждого REGISTERED Attendance в этом instance:
   │  ├─ Membership.balance -= 1
   │  ├─ Attendance.status = ATTENDED
   │  └─ Если balance == 0 → Membership.status = EXHAUSTED
   │      └─ 🟢 TRIGGER: запускаются процессы (один раз, точно)
   └─ Все или ничего (TRANSACTION)

5. Если ошибка (отметили не того клиента):
   └─ RemoveAttendance(attendanceId) // while instance still SCHEDULED
      ├─ Attendance deleted
      ├─ Membership UNCHANGED (balance не менялся)
      └─ Никакие процессы не откатываются! ✓
```

### Преимущества этого подхода

✅ **Отсутствие каскадных откатов**
```kotlin
// Клиент отметил не того? Просто удалите Attendance из черновика
RemoveAttendance(attendanceId)  // No side effects!

// Все clean и просто
```

✅ **Транзакция "всё или ничего"**
```kotlin
// MarkInstanceAsCompleted выполняется одной транзакцией
transaction {
    instanceRepo.updateStatus(COMPLETED)
    
    for (attendance in attendances) {
        membershipRepo.deduct(attendance.membershipId)  // atomic
        attendanceRepo.updateStatus(ATTENDED)
    }
    
    // Если что-то сломалось → всё откатывается
    // Нет полуживых состояний
}
```

✅ **Избавление от race conditions**
```kotlin
// Список зарегистрированных клиентов создан на МОМЕНТ CheckinClient
// Даже если кто-то случайно нажмет дважды:
CheckinClient("ivanov")  // Attendance #1 created
CheckinClient("ivanov")  // Attendance #2 created (разные ID!)

// При MarkInstanceAsCompleted система видит TWO attendance для одного клиента
// и может либо:
// - Отклонить (ошибка)
// - Дедупликировать (взять только первый)
// - Создать задачу для тренера: "Иванов отмечен дважды?"
```

✅ **Меньше состояния в абонементе**
```
Membership.balance всегда отражает ИСТИНУ только в moment-of-truth
= когда занятие ТОЧНО проведено

Между CheckinClient и MarkInstanceAsCompleted:
  Attendance.status = REGISTERED (черновик)
  Membership.balance не меняется
  
Нет расхождения между "записан" и "на счету"
```

✅ **Легче откатить целое занятие**
```kotlin
// Тренер заболел, нужно отменить все занятие:
CancelInstance(instanceId)
  ├─ GroupInstance.status = CANCELLED
  ├─ Все Attendance.status = CANCELLED (были REGISTERED)
  ├─ Membership balance НЕ менялась (он не вычитывалась)
  └─ ✓ Никакие уведомления не отправлялись!

vs.

// Вариант A: пришлось бы откатывать каждое посещение
for (attendance in attendances) {
    CancelCheckin(attendance.id)  // восстанавливаем каждое
}
// Много операций, много risk
```

✅ **Явный контроль над состоянием**
```
Тренер ВИДИТ:
  - Registered (tentative, можно удалить)
  - Completed (окончательно)
  - Cancelled (отменено)

Вместо неявного "была ли отметка" / "был ли откат"
```

---

## 📊 Сравнение подходов

| Аспект | Вариант A (немедленно) | Вариант B (отложено) |
|--------|----------------------|----------------------|
| **Когда списываем** | При CheckinClient | При MarkInstanceAsCompleted |
| **Простота реализации** | Проще (1 операция) | Сложнее (2 операции) |
| **Откаты** | ❌ Сложные (каскадные эффекты) | ✅ Простые (удаление Attendance) |
| **Race conditions** | ❌ Возможны (need lock) | ✅ Маловероятны (разные таблицы) |
| **Ошибки дублирования** | ❌ Возможны | ✅ Поймаются при MarkInstanceAsCompleted |
| **Асинхронные эффекты** | ❌ Могут потребовать отката | ✅ Только финальные, точные |
| **Состояние абонемента** | ⚠️ Может быть неточным | ✅ Всегда точное |
| **Отмена целого занятия** | ❌ Сложно (откатывать каждое посещение) | ✅ Просто (просто CANCELLED) |
| **Мобильное приложение** | ❌ Offline сложнее | ✅ Проще (checkout в конце) |
| **Аналитика в реальном времени** | ✅ Можно (balance обновлен) | ⚠️ Нужна эстимация |

---

## 🎯 Рекомендация

### Для MVP (фаза 1): **Вариант B (отложенное списание)**

**Почему?**

1. **Меньше risk** — откаты простые, нет каскадных эффектов
2. **Меньше bug-ов** — нет race conditions с balance
3. **Проще поддерживать** — два чистых состояния (REGISTERED, ATTENDED)
4. **Экологичнее для асинхронности** — события запускаются только на финал
5. **Лучше для отмены занятий** — CancelInstance не требует каскадных откатов

### Примерная реализация

```kotlin
// Поток B (отложенное списание)

// ============================================
// ШАГ 1: Регистрация посещения (в начале занятия)
// ============================================
suspend fun checkInClient(
    instanceId: UUID, 
    clientId: UUID
): Either<Error, AttendanceDto> = either {
    // Валидации
    val instance = groupInstanceRepo.findById(instanceId)
        ?.takeIf { it.status == InstanceStatus.SCHEDULED }
        ?: raise(InstanceNotFound())
    
    val membership = membershipRepo.findActiveForClient(clientId, instance.groupId)
        ?: raise(NoActiveMembership())
    
    // Проверка дубля
    val existing = attendanceRepo.findByInstanceAndClient(instanceId, clientId)
    if (existing != null) {
        raise(AlreadyCheckedIn())  // или update?
    }
    
    // Создаем черновик (REGISTERED)
    val attendance = Attendance(
        id = UUID.randomUUID(),
        instanceId = instanceId,
        clientId = clientId,
        membershipId = membership.id,
        status = AttendanceStatus.REGISTERED,  // ← Черновик!
        registeredAt = Instant.now()
    )
    
    attendanceRepo.save(attendance)
    
    return AttendanceDto(
        id = attendance.id,
        status = "registered",  // ← Уточнить с фронтом: это черновик!
        message = "Внимание: списание произойдет после завершения занятия"
    )
}

// ============================================
// ШАГ 2: Завершение занятия (в конце)
// ============================================
suspend fun markInstanceAsCompleted(
    instanceId: UUID
): Either<Error, InstanceCompletedDto> = either {
    val instance = groupInstanceRepo.findById(instanceId)
        ?.takeIf { it.status == InstanceStatus.SCHEDULED }
        ?: raise(InstanceNotFound())
    
    val registeredAttendances = attendanceRepo.findByInstance(instanceId)
        .filter { it.status == AttendanceStatus.REGISTERED }
    
    // ATOMIC TRANSACTION
    transaction {
        // 1. Обновляем занятие
        instance.status = InstanceStatus.COMPLETED
        instance.completedAt = Instant.now()
        groupInstanceRepo.save(instance)
        
        // 2. Для каждого посещения
        for (attendance in registeredAttendances) {
            // Находим абонемент
            val membership = membershipRepo.findById(attendance.membershipId)
                ?: raise(MembershipNotFound())
            
            // Проверяем что абонемент все еще активен
            // (может быть, истек за время между CheckIn и Complete)
            if (membership.status != MembershipStatus.ACTIVE) {
                raise(MembershipExpired())  // Или warn + skip?
            }
            
            // Списываем
            membership.balance -= 1
            
            // Проверяем статус
            if (membership.balance <= 0) {
                membership.status = MembershipStatus.EXHAUSTED
                // 🟢 Асинхронно (но ОДИН раз, не в loop!):
                // - Отправить уведомление
                // - Создать задачу менеджеру
                // - Добавить в churnList
            }
            
            membershipRepo.save(membership)
            
            // Отмечаем посещение как финальное
            attendance.status = AttendanceStatus.ATTENDED
            attendance.attendedAt = Instant.now()
            attendanceRepo.save(attendance)
        }
        
        // Trigger async events (только один раз, после всех обновлений!)
        eventPublisher.publishInstanceCompleted(instance, registeredAttendances)
    }
    
    return InstanceCompletedDto(
        instanceId = instanceId,
        attendanceCount = registeredAttendances.size,
        updated = true
    )
}

// ============================================
// ШАГ 3: Откат (если ошибка при CheckIn)
// ============================================
suspend fun removeAttendance(
    attendanceId: UUID
): Either<Error, Unit> = either {
    val attendance = attendanceRepo.findById(attendanceId)
        ?: raise(AttendanceNotFound())
    
    // Можно удалять только REGISTERED (черновики)
    // Если уже ATTENDED → error, нужно CancelAttendance (другой путь)
    if (attendance.status != AttendanceStatus.REGISTERED) {
        raise(CannotRemoveCompletedAttendance())
    }
    
    attendanceRepo.delete(attendance)
    
    // Никакие эффекты не откатываются! Balance не менялся.
    // Просто и чисто.
}

// ============================================
// ШАГ 3b: Отмена уже состоявшегося посещения (отвалидирована позже)
// ============================================
suspend fun cancelAttendance(
    attendanceId: UUID
): Either<Error, Unit> = either {
    val attendance = attendanceRepo.findById(attendanceId)
        ?: raise(AttendanceNotFound())
    
    // Можно отменять только ATTENDED
    if (attendance.status != AttendanceStatus.ATTENDED) {
        raise(CannotCancelUnattendedAttendance())
    }
    
    transaction {
        // Откатываем в абонементе
        val membership = membershipRepo.findById(attendance.membershipId)!!
        membership.balance += 1
        
        // Если был EXHAUSTED → вернуть в ACTIVE
        if (membership.status == MembershipStatus.EXHAUSTED) {
            membership.status = MembershipStatus.ACTIVE
        }
        
        membershipRepo.save(membership)
        
        // Отмечаем посещение как отменённое
        attendance.status = AttendanceStatus.CANCELLED
        attendanceRepo.save(attendance)
        
        // Trigger async: уведомить что посещение отменено (опционально)
        eventPublisher.publishAttendanceCancelled(attendance)
    }
}
```

### Состояния (State Machine)

```
┌─────────────────────────────────────────────────────────┐
│             ATTENDANCE State Machine                    │
└─────────────────────────────────────────────────────────┘

REGISTERED (черновик)
    ├─ [removeAttendance] → DELETED (в БД удален)
    │
    ├─ [markInstanceAsCompleted (batch)] → ATTENDED ✓
    │                                     ↓
    │                                  balance -= 1
    │
    └─ [если отмена instance] → CANCELLED

ATTENDED (финализировано)
    ├─ [cancelAttendance] → CANCELLED
    │                      ↓
    │                   balance += 1
    │
    └─ [история, read-only]

CANCELLED
    └─ [история]
```

---

## 🔌 Интеграция с фронтом

### Для веб-приложения тренера

```typescript
// До MarkInstanceAsCompleted
const instance = {
  status: 'scheduled',
  attendances: [
    { id: 1, client: 'Иван', status: 'registered', balanceBefore: 10 },
    { id: 2, client: 'Мария', status: 'registered', balanceBefore: 5 }
  ],
  disclaimer: 'Баланс будет списан после завершения занятия'
}

// После MarkInstanceAsCompleted
const instance = {
  status: 'completed',
  attendances: [
    { id: 1, client: 'Иван', status: 'attended', balanceBefore: 10, balanceAfter: 9 },
    { id: 2, client: 'Мария', status: 'attended', balanceBefore: 5, balanceAfter: 4 }
  ]
}
```

### Для мобильного приложения клиента

```typescript
// Если есть offline mode:
// Клиент может видеть что он "registered" на занятие
// Но баланс не расходуется до sync с сервером

// После sync:
// Если занятие completed → баланс обновляется
// Если занятие отменено → баланс не меняется
```

---

## ⚠️ Edge cases Варианта B

### Case 1: Абонемент истекает между CheckIn и MarkInstanceAsCompleted
```kotlin
// 10:00 CheckinClient("ivanov")
//   ✓ Membership.status = ACTIVE
//   ✓ Attendance.status = REGISTERED

// ... занятие идёт ...

// 12:00 Membership автоматически истекает
//   Membership.status = EXPIRED
//   (фоновый процесс)

// 13:00 MarkInstanceAsCompleted()
//   ✗ EXPIRED membership
//   
// Решение: Либо error, либо все равно списываем с комментарием "late completion"
// Рекомендация: Error + UI говорит тренеру "свяжись с администратором"
```

### Case 2: Удаление клиента из GroupInstance между CheckIn и Complete
```kotlin
// Тренер отметил Ивана
// Админ удалил Ивана из группы
// При MarkInstanceAsCompleted пытаемся списать с удалённого membership

// Решение: Поле membershipId в Attendance (историческая ссылка)
// не удаляется, даже если membership удалён
// Можно списать "жёсткий откат"
```

### Case 3: Баланс стал отрицательным (двойная ошибка)
```kotlin
// Теоретически невозможно в Варианте B (single transaction)
// Но если всё равно:
if (membership.balance < 0) {
    // Логируем как ERROR, уведомляем adminа
    // balance = 0 (защита)
}
```

---

## 🚀 Миграция с Варианта A на Вариант B

Если вы уже реализовали Вариант A, можно перейти:

1. **Добавить новый статус** `Attendance.REGISTERED` и `ATTENDED`
2. **Создать MarkInstanceAsCompleted** юзкейс
3. **Найти все старые ATTENDED** и пересчитать балансы (если нужно)
4. **Обновить CheckinClient** чтобы создавал REGISTERED вместо ATTENDED
5. **Обновить фронт** чтобы требовал MarkInstanceAsCompleted

---

## ✅ Final Recommendation

| Для MVP | Вариант B ✅ |
|---------|-------------|
| **Реализовать** | CheckinClient (→ REGISTERED) |
| | MarkInstanceAsCompleted (→ ATTENDED + deduct) |
| | RemoveAttendance (→ удалить черновик) |
| | CancelAttendance (→ CANCELLED + restore) |
| | CancelInstance (→ отменить все в черновике) |
| **NOT реализовать** | Запутанные откаты с асинхронными эффектами |
| **Выиграть** | Чистая архитектура, меньше bugs, легче поддерживать |

**Это стоит потраченного времени на реализацию двух операций вместо одной.**
