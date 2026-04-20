package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.auth.Users
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

data class DbEmployee(
    override val id: EmployeeId,
    override val userId: UserId?,
    override val name: String,
    override val avatarId: UploadId?,
    override val isOwner: Boolean,
    override val isActive: Boolean,
    override val joinedAt: Instant,
    override val phoneNo: String?,
    override val email: EmailAddress?,
    override val permissions: EmployeePermission,
    private val users: Users,
) : Employee {
    context(ctx: RequestContext, tr: Transaction)
    override suspend fun save() {
        tr.sql(
            """
            UPDATE employees
            SET user_id    = :userId,
                is_active  = :isActive,
                name       = :name,
                avatar_id  = :avatarId,
                phone_no   = :phoneNo,
                email      = :email
            WHERE id = :id AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("userId", userId)
            .bind("isActive", isActive)
            .bind("name", name)
            .bind("avatarId", avatarId)
            .bind("phoneNo", phoneNo)
            .bind("email", email)
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()

        tr.sql("DELETE FROM employee_permission_overrides WHERE employee_id = :id")
            .bind("id", id)
            .execute()

        for (permission in permissions.grantedPermissions) {
            tr.sql(
                """
                INSERT INTO employee_permission_overrides (employee_id, permission_key, is_granted)
                VALUES (:employeeId, :key, true)
                """.trimIndent(),
            )
                .bind("employeeId", id)
                .bind("key", permission.name)
                .execute()
        }

        for (permission in permissions.revokedPermissions) {
            tr.sql(
                """
                INSERT INTO employee_permission_overrides (employee_id, permission_key, is_granted)
                VALUES (:employeeId, :key, false)
                """.trimIndent(),
            )
                .bind("employeeId", id)
                .bind("key", permission.name)
                .execute()
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun invite(email: EmailAddress, password: String) {
        val user = users.new(email.value, password)
        copy(userId = user.id, isActive = true, email = email).save()
    }

    context(ctx: RequestContext)
    override fun withNew(
        newName: String,
        newPermissions: EmployeePermission,
        newAvatarId: UploadId?,
        newPhoneNo: String?,
        newEmail: EmailAddress?,
    ): Employee =
        copy(
            name = newName,
            permissions = newPermissions,
            email = newEmail,
            avatarId = newAvatarId,
            phoneNo = newPhoneNo,
        )
}
