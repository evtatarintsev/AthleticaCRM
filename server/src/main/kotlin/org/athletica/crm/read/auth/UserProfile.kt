package org.athletica.crm.read.auth

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.security.UserNotFound
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import java.util.UUID
import kotlin.uuid.toKotlinUuid

/**
 * Возвращает профиль текущего авторизованного пользователя.
 * Использует [ctx] для получения идентификатора пользователя из JWT.
 * Возвращает [UserProfile]; если пользователь не найден — поднимает [UserNotFound].
 */
context(db: Database, ctx: EmployeeRequestContext, raise: Raise<DomainError>)
suspend fun profile(): UserProfile =
    db
        .sql(
            """
            SELECT u.id, u.login, e.id as employee_id, e.org_id, e.name, e.avatar_id,
                   b.id as branch_id, b.name as branch_name
            FROM users u
            JOIN employees e ON e.user_id = u.id AND e.org_id = :orgId
            JOIN branches b ON b.id = :branchId AND b.org_id = e.org_id
            WHERE u.id = :id AND e.is_active = true
            """.trimIndent(),
        )
        .bind("id", ctx.userId)
        .bind("orgId", ctx.orgId)
        .bind("branchId", ctx.branchId)
        .firstOrNull { it.toUserProfile() }
        ?: raise(UserNotFound("User with id='${ctx.userId}' not found"))

data class UserProfile(
    val id: UserId,
    val orgId: OrgId,
    val employeeId: EmployeeId,
    val username: String,
    val name: String,
    val avatarId: UploadId? = null,
    val currentBranchId: BranchId,
    val currentBranchName: String,
)

private fun Row.toUserProfile() =
    UserProfile(
        id = asUuid("id").toUserId(),
        orgId = asUuid("org_id").toOrgId(),
        employeeId = asUuid("employee_id").toEmployeeId(),
        username = asString("login"),
        name = asString("name"),
        avatarId = get("avatar_id", UUID::class.java)?.toKotlinUuid()?.toUploadId(),
        currentBranchId = asUuid("branch_id").toBranchId(),
        currentBranchName = asStringOrNull("branch_name") ?: "",
    )
