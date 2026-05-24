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

    /** Позволяет видеть задачи всех сотрудников, а не только свои. */
    CAN_VIEW_ALL_TASKS,

    /** Позволяет создавать и редактировать чужие задачи. */
    CAN_MANAGE_TASKS,
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
