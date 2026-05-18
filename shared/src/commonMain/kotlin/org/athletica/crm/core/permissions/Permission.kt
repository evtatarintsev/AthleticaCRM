package org.athletica.crm.core.permissions

enum class Permission {
    CAN_MANAGE_ORG_BALANCE,
    CAN_VIEW_CLIENT_BALANCE,

    /** Позволяет видеть задачи всех сотрудников, а не только свои. */
    CAN_VIEW_ALL_TASKS,

    /** Позволяет создавать и редактировать чужие задачи. */
    CAN_MANAGE_TASKS,
}

interface Actor {
    fun hasPermission(permission: Permission): Boolean
}
