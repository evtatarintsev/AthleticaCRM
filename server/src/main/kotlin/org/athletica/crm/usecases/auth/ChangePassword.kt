package org.athletica.crm.usecases.auth

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.ChangePasswordRequest
import org.athletica.crm.core.PasswordHash
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logChangePassword
import org.athletica.crm.i18n.Messages
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.security.userById
import org.athletica.crm.storage.Transaction

/**
 * Меняет пароль текущего авторизованного пользователя.
 *
 * Проверяет [request.oldPassword] против сохранённого хэша, затем
 * обновляет `users.password_hash` новым хэшем Argon2id.
 * Записывает событие [AUTH_CHANGE_PASSWORD] в журнал аудита.
 *
 * Возвращает [Unit] при успехе или [DomainError] при неверном старом пароле / отсутствии пользователя.
 */
context(ctx: RequestContext, tr: Transaction, passwordHasher: PasswordHasher, audit: AuditLog, raise: Raise<DomainError>)
suspend fun changePassword(request: ChangePasswordRequest) {
    val user = userById(ctx.userId)

    val oldPasswordValid = passwordHasher.verify(request.oldPassword, PasswordHash(user.password))
    if (!oldPasswordValid) {
        raise(CommonDomainError(code = "WRONG_PASSWORD", message = Messages.WrongPassword.localize()))
    }

    val newHash = passwordHasher.hash(request.newPassword)

    tr
        .sql("UPDATE users SET password_hash = :hash WHERE id = :id")
        .bind("hash", newHash.value)
        .bind("id", ctx.userId)
        .execute()

    audit.logChangePassword()
}
