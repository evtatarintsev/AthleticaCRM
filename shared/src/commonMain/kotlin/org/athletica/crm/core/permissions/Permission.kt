package org.athletica.crm.core.permissions

/** Базовый sealed-тип для всех прав в системе. */
sealed interface Permission

/**
 * Права, которые могут быть выданы сотруднику организации.
 * Проверяются через [UserActor.hasPermission].
 */
enum class UserPermission : Permission {
    CAN_MANAGE_ORG_BALANCE,
    CAN_VIEW_CLIENT_BALANCE,

    /** Позволяет принимать оплаты клиентов и отменять свои сегодняшние оплаты. */
    CAN_ACCEPT_PAYMENTS,

    /** Позволяет управлять кассами, статьями и финансовыми операциями организации. */
    CAN_MANAGE_FINANCE,

    /** Позволяет просматривать кассовый журнал, остатки касс и отчёт ДДС. */
    CAN_VIEW_FINANCE,

    /** Позволяет видеть задачи всех сотрудников, а не только свои. */
    CAN_VIEW_ALL_TASKS,

    /** Позволяет создавать и редактировать чужие задачи. */
    CAN_MANAGE_TASKS,

    /** Позволяет использовать AI-помощника организации. */
    CAN_USE_ASSISTANT,

    /** Позволяет создавать, настраивать и удалять виджеты расписания для сайта и ВКонтакте. */
    CAN_MANAGE_WIDGETS,
}

/**
 * Права для системных фоновых операций (cron, event handlers).
 * Сотрудникам не выдаются ни при каких обстоятельствах.
 * Пока пусто — заготовка для будущих системных прав.
 */
enum class SystemPermission : Permission

/** Actor, способный проверять пользовательские права ([UserPermission]). */
interface UserActor {
    fun hasPermission(p: UserPermission): Boolean
}

/** Actor, способный проверять системные права ([SystemPermission]). */
interface SystemActor {
    fun hasPermission(p: SystemPermission): Boolean
}
