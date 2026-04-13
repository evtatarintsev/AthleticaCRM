# 📋 Планирование CRM для спортивных организаций

## 📁 Структура документов планирования

```
AthleticaCRM/
├── GLOSSARY.md                ← Единый язык (Ubiquitous Language DDD) — начните отсюда
├── USECASES_CORE.md          ← Детальное описание 28+ юзкейсов по 4 модулям
├── ARCHITECTURE_CORE.md       ← Архитектура, модель данных, бизнес-правила
├── PLANNING_SUMMARY.md        ← MVP планы, timeline, критичные решения
├── QUICK_REFERENCE.md         ← Быстрая справка и чек-листы
└── README_PLANNING.md         ← Этот файл (навигация)
```

---

## 🚀 Начните отсюда

### 1️⃣ Если вам нужен быстрый обзор (15 минут)
👉 Читайте **QUICK_REFERENCE.md**
- Таблица всех 30+ юзкейсов
- MVP Phase 1: 10 критичных UC
- Критичные потоки данных
- Чек-листы для разработки

### 2️⃣ Если вам нужен план на реализацию (30 минут)
👉 Читайте **PLANNING_SUMMARY.md**
- Что уже реализовано ✅
- MVP Phase 1 (4 недели)
- Phase 2 (6 недель)
- Timeline и estimate часов
- 5 вопросов для обсуждения

### 3️⃣ Если вам нужны полные детали (2-3 часа)
👉 Читайте **USECASES_CORE.md**
- По модулю 1: Клиентская база (8 UC)
- По модулю 2: Абонементы (13 UC)
- По модулю 3: Посещаемость (8 UC)
- По модулю 4: Расписание (8 UC)
- Матрица приоритизации
- Рекомендуемый порядок разработки

### 4️⃣ Если вам нужна архитектура (2-3 часа)
👉 Читайте **ARCHITECTURE_CORE.md**
- ER диаграмма сущностей
- 4 критичные потока данных (A, B, C, D)
- 20+ бизнес-правил
- State machines для Membership, Instance, Attendance
- Edge cases и их решения
- Чек-лист для каждого UC

---

## 📊 Краткий обзор планирования

### Текущее состояние
| Модуль | Реализовано | Осталось |
|--------|-------------|----------|
| Клиентская база | 60% | 40% |
| Абонементы | 0% | 100% |
| Посещаемость | 0% | 100% |
| Расписание | 30% | 70% |
| **ИТОГО** | **23%** | **77%** |

### MVP Phase 1 (10 базовых UC)
1. ✅ CreateMembershipPlan (управление планами абонементов)
2. ✅ CreateMembership (продажа абонементов)
3. ✅ RecordPayment (регистрация оплаты)
4. ✅ GenerateSessions (планирование занятий)
5. ✅ AssignTrainerToSession (назначение тренера)
6. ✅ CheckinClient (отметка посещения)
7. ✅ DeductMembership (убыль занятий)
8. ✅ CancelCheckin (отмена посещения)
9. ✅ CreateRoom (управление залами)
10. ✅ AssignGroupToRoom (привязка групп к залам)

**Время:** ~10 недель (1 senior разработчик)

### Phase 2 (аналитика, маркетинг, интеграции)
- Отчеты по посещаемости и churn
- Расширенная управление планами абонементов (сезоны, льготы)
- Интеграция платежных систем
- Email/SMS уведомления

---

## 🎯 Архитектурные решения

### Основные сущности (7)
```
Client → Membership ← MembershipPlan
  ↓           ↓
Attendance → Session
              ↓
            Room
```

### Критичные потоки
- **A**: Покупка абонемента (Admin → CreateMembership → RecordPayment)
- **B**: Посещение (Trainer → CheckinClient → DeductMembership)
- **C**: Отмена (CancelCheckin → RestoreMembership)
- **D**: Истечение (Background job → ExpireMembership)

### Status машины
- **Membership**: pending → active → {expired, exhausted, cancelled}
- **Instance**: scheduled → {completed, cancelled}
- **Attendance**: attended / no_show / cancelled

---

## 🔍 Граничные случаи (пожелание)

Документы содержат решения для:
- Двойное посещение (конкуренция)
- Истечение баланса абонемента
- Переход между часовыми поясами
- Отмена всей группы
- И ещё 10+ case-ов

---

## 💼 Размер проекта

| Метрика | Значение |
|---------|----------|
| Новых таблиц | 6-8 |
| Новых юзкейсов | ~25 |
| Новых endpoints | ~30-35 |
| Новых моделей | ~15-20 |
| Estimated часов | 80 |
| Estimated недель | 10-14 |
| Целевая аудитория | Организации до 1000 клиентов |

---

## ❓ Вопросы для обсуждения перед стартом

1. **Что такое "группа"?** Это группа занятий (класс) или сегмент клиентов?
2. **День-в-день?** Списание денег при покупке или при посещении?
3. **Возврат абонемента?** Поддерживаем ли в Phase 1 или Phase 2?
4. **Переносимость занятий?** Может ли клиент посетить другое занятие вместо запланированного?
5. **Интеграция платежей?** Stripe/Yandex.Kassa в Phase 1 или Phase 2?

👉 Детали в **PLANNING_SUMMARY.md**, раздел "Критичные решения"

---

## 🛠️ Quick Start для разработчиков

### Что нужно сделать ПЕРЕД кодированием
1. Обсудить 5 вопросов выше
2. Создать миграции БД (6-8 новых таблиц)
3. Создать Kotlin data classes (Request/Response)
4. Создать Error codes
5. Настроить fixtures для тестов
6. Выбрать инструмент для Background Jobs

### Рекомендуемый порядок разработки
```
Спринт 1: MembershipPlan + Membership (CreateMembershipPlan, CreateMembership, RecordPayment)
  ↓
Спринт 2: Attendance + Deduction (CheckinClient, DeductMembership, CancelCheckin)
  ↓
Спринт 3: Planning (GenerateSessions, AssignTrainerToSession)
  ↓
Спринт 4: Rooms (CreateRoom, AssignGroupToRoom, RoomAvailability)
  ↓
Спринт 5: Extensions (UpdateClientInfo, ClientActivityTimeline, etc)
```

---

## 📚 Как использовать эти документы

### Для Product Owner / Менеджера
→ **PLANNING_SUMMARY.md** + **QUICK_REFERENCE.md** (1-2 часа)
- Понять scope и timeline
- Утвердить MVP
- Обсудить критичные решения

### Для Senior разработчика / Архитектора
→ **ARCHITECTURE_CORE.md** (3-4 часа)
- Спланировать DB schema
- Продумать API contracts
- Определить edge cases
- Выбрать технологии

### Для Junior разработчика / Разработчика
→ **USECASES_CORE.md** (2-3 часа)
- Понять каждый UC в деталях
- Посмотреть примеры потоков
- Использовать как гайд при разработке

### Для QA / Тестировщика
→ **ARCHITECTURE_CORE.md** + **QUICK_REFERENCE.md**
- Критичные потоки (A, B, C, D)
- Edge cases для тестирования
- Чек-листы для каждого UC

---

## 📞 Вопросы или изменения?

Если что-то нужно уточнить или изменить:
1. Отредактируйте соответствующий документ
2. Обсудите с командой
3. Создайте JIRA/GitHub issues на основе UC-ов

---

## ✅ Статус документации

| Документ | Статус | Версия |
|----------|--------|--------|
| USECASES_CORE.md | ✅ Готово | 1.0 |
| ARCHITECTURE_CORE.md | ✅ Готово | 1.0 |
| PLANNING_SUMMARY.md | ✅ Готово | 1.0 |
| QUICK_REFERENCE.md | ✅ Готово | 1.0 |
| DB Schema (SQL) | ⏳ TO DO | - |
| API Specification (OpenAPI) | ⏳ TO DO | - |
| Test Strategy | ⏳ TO DO | - |

---

**Дата создания:** 2026-04-11  
**Автор:** AI Assistant (Claude 3.5 Sonnet)  
**Статус:** Draft для обсуждения и утверждения
