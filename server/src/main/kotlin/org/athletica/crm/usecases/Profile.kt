package org.athletica.crm.usecases

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import io.r2dbc.spi.Row
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UserId
import org.athletica.crm.core.auth.AuthenticatedUser
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toOrgId
import org.athletica.crm.core.toUserId
import org.athletica.crm.db.Database
import org.athletica.crm.security.User
import org.athletica.crm.security.UserNotFound
import kotlin.uuid.Uuid
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


data class UserProfile(
    override val id: UserId,
    override val orgId: OrgId,
    override val username: String,
    val name: String,
    val avatarId: Uuid? = null,
) : AuthenticatedUser


private fun Row.toUserProfile() =
    UserProfile(
        id = get("id", java.util.UUID::class.java)!!.toKotlinUuid().toUserId(),
        orgId = get("org_id", java.util.UUID::class.java)!!.toKotlinUuid().toOrgId(),
        username = get("login", String::class.java)!!,
        name = get("name", String::class.java)!!,
        avatarId = get("avatar_id", java.util.UUID::class.java)?.toKotlinUuid(),
    )
