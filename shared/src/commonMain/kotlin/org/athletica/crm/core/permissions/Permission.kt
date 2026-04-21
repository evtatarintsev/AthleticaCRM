package org.athletica.crm.core.permissions

enum class Permission {
    CAN_MANAGE_ORG_BALANCE,
    CAN_VIEW_CLIENT_BALANCE,
}

interface Actor {
    fun hasPermission(permission: Permission): Boolean
}
