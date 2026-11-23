package org.athletica.crm.core.audit

/** Типы действий, которые логируются в системе аудита. */
enum class AuditActionType(val code: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    AUTH_LOGIN("auth_login"),
    AUTH_LOGOUT("auth_logout"),
    AUTH_SIGNUP("auth_signup"),
    AUTH_VERIFY_EMAIL("auth_verify_email"),
    MESSAGE_SEND("message_send"),
    EXPORT("export"),
    IMPORT("import"),
    AUTH_CHANGE_PASSWORD("auth_change_password"),
    AUTH_PASSWORD_RESET("auth_password_reset"),
    BALANCE_ADJUST("balance_adjust"),
}
