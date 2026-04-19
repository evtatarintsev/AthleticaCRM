package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.UserId
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
    override val roles: List<EmployeeRole>,
    override val phoneNo: String?,
    override val email: EmailAddress?,
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
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun invite(email: EmailAddress, password: String) {
        val user = users.new(email.value, password)
        copy(userId = user.id, isActive = true, email = email).save()
    }
}
