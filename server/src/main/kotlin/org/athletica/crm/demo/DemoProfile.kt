package org.athletica.crm.demo

/**
 * Профиль объёма наполнения: [clients] клиентов, [groups] групп, [branches] филиалов
 * и история работы длиной [historyMonths] месяцев до сегодняшнего дня.
 */
enum class DemoProfile(
    /** Число клиентов, пришедших за период истории. */
    val clients: Int,
    /** Число групп, открытых за период истории. */
    val groups: Int,
    /** Число филиалов вместе с филиалом регистрации. */
    val branches: Int,
    /** Длина периода истории в месяцах. */
    val historyMonths: Int,
) {
    /** Небольшая организация: локальная проверка и демо-доступ. */
    SMALL(clients = 60, groups = 6, branches = 2, historyMonths = 4),

    /** Средняя организация: экраны с заметными объёмами. */
    MEDIUM(clients = 300, groups = 15, branches = 3, historyMonths = 8),

    /** Крупная организация: пагинация и производительность списков. */
    LARGE(clients = 1500, groups = 40, branches = 4, historyMonths = 12),
}

/** Итог наполнения для вывода команды: сколько создано клиентов, групп, занятий и продаж. */
data class DemoSummary(
    /** Число созданных клиентов. */
    val clients: Int,
    /** Число созданных групп. */
    val groups: Int,
    /** Число занятий организации, включая будущие по расписанию. */
    val sessions: Int,
    /** Число оформленных продаж. */
    val sales: Int,
)
