package org.athletica.crm.usecases.auth

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.ChangePasswordRequest
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logChangePassword
import org.athletica.crm.core.PasswordHash
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.db.Database
import org.athletica.crm.security.PasswordHasher
import org.athletica.crm.security.userById

/**
 * Меняет пароль текущего авторизованного пользователя.
 *
 * Проверяет [request.oldPassword] против сохранённого хэша, затем
 * обновляет `users.password_hash` новым хэшем Argon2id.
 * Записывает событие [AUTH_CHANGE_PASSWORD] в журнал аудита.
 *
 * Возвращает [Unit] при успехе или [DomainError] при неверном старом пароле / отсутствии пользователя.
 */
context(db: Database, ctx: RequestContext, passwordHasher: PasswordHasher, audit: AuditLog)
suspend fun changePassword(request: ChangePasswordRequest): Either<DomainError, Unit> =
    either {
        val user = userById(ctx.userId).bind()

        val oldPasswordValid = passwordHasher.verify(request.oldPassword, PasswordHash(user.password))
        if (!oldPasswordValid) {
            raise(CommonDomainError(code = "WRONG_PASSWORD", message = "Неверный текущий пароль"))
        }

        val newHash = passwordHasher.hash(request.newPassword)

        db
            .sql("UPDATE users SET password_hash = :hash WHERE id = :id")
            .bind("hash", newHash.value)
            .bind("id", ctx.userId)
            .execute()

        audit.logChangePassword()
    }
