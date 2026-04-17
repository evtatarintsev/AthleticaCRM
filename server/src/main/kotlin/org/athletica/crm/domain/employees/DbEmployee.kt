package org.athletica.crm.domain.employees

import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.OrgId
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.UserId
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

internal data class DbEmployee(
    override val id: EmployeeId,
    override val userId: UserId?,
    override val name: String,
    override val avatarId: UploadId?,
    override val isOwner: Boolean,
    override val isActive: Boolean,
    override val joinedAt: Instant,
    override val roles: List<EmployeeRole>,
    override val phoneNo: String?,
    override val email: String?,
    private val orgId: OrgId,
) : Employee {
    context(tr: Transaction)
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
            .bind("orgId", orgId)
            .execute()
    }

    override fun withUserId(userId: UserId): Employee = copy(userId = userId)

    override fun activate(): Employee = copy(isActive = true)
}
