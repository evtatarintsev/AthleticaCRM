package org.athletica.crm.usecases.auth

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.read.auth.UserProfile
import org.athletica.crm.read.auth.profile
import org.athletica.crm.security.UserNotFound
import org.athletica.crm.storage.Database

/**
 * Обновляет имя и аватар текущего авторизованного сотрудника.
 * Изменения применяются к записи [employees] организации из [ctx].
 * Возвращает обновлённый [UserProfile] или ошибку если сотрудник не найден.
 */
context(db: Database, ctx: EmployeeRequestContext)
suspend fun updateMe(request: UpdateMeRequest): Either<DomainError, UserProfile> =
    either {
        val updatedRows =
            db
                .sql("UPDATE employees SET name = :name, avatar_id = :avatarId WHERE user_id = :userId AND org_id = :orgId")
                .bind("name", request.name)
                .bind("avatarId", request.avatarId)
                .bind("userId", ctx.userId)
                .bind("orgId", ctx.orgId)
                .execute()

        if (updatedRows == 0L) raise(UserNotFound("Employee not found for user='${ctx.userId}'"))

        profile().bind()
    }
