package org.athletica.crm.usecases.auth

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.OrgInfo
import org.athletica.crm.api.schemas.UpdateMeRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.security.UserNotFound
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asDouble
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import java.util.UUID
import kotlin.uuid.toKotlinUuid

/**
 * Возвращает профиль текущего авторизованного пользователя.
 * Использует [ctx] для получения идентификатора пользователя из JWT.
 * Возвращает [User] или [CommonDomainError], если пользователь не найден.
 */
context(db: Database, ctx: RequestContext)
suspend fun profile(): Either<DomainError, UserProfile> =
    db
        .sql(
            """
            SELECT u.id, u.login, u.password_hash, e.org_id, e.name, e.avatar_id
            FROM users u
            JOIN employees e ON e.user_id = u.id AND e.org_id = :orgId
            WHERE u.id = :id AND e.is_active = true
            """.trimIndent(),
        )
        .bind("id", ctx.userId)
        .bind("orgId", ctx.orgId)
        .firstOrNull { it.toUserProfile() }
        ?.right() ?: UserNotFound("User with id='${ctx.userId}' not found").left()

/**
 * Обновляет имя и аватар текущего авторизованного сотрудника.
 * Изменения применяются к записи [employees] организации из [ctx].
 * Возвращает обновлённый [UserProfile] или ошибку если сотрудник не найден.
 */
context(db: Database, ctx: RequestContext)
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

data class UserProfile(
    override val id: UserId,
    override val orgId: OrgId,
    override val username: String,
    val name: String,
    val avatarId: UploadId? = null,
) : AuthenticatedUser

private fun Row.toUserProfile() =
    UserProfile(
        id = asUuid("id").toUserId(),
        orgId = asUuid("org_id").toOrgId(),
        username = asString("login"),
        name = asString("name"),
        avatarId = get("avatar_id", UUID::class.java)?.toKotlinUuid()?.toUploadId(),
    )
