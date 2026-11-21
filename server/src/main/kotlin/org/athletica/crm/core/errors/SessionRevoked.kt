package org.athletica.crm.core.errors

/** Сессия токена завершена или не существует: запрос отклоняется с HTTP 401, как при недействительном токене. */
data object SessionRevoked : AppError {
    override val code = "SESSION_REVOKED"
    override val message = "Session is revoked"
}
