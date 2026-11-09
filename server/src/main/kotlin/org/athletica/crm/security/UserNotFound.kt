package org.athletica.crm.security

import org.athletica.crm.core.errors.DomainError

/** Ошибка поиска пользователя: пользователь не найден по заданным критериям. */
data class UserNotFound(override val message: String) : DomainError {
    override val code: String = "USER_NOT_FOUND"
}
