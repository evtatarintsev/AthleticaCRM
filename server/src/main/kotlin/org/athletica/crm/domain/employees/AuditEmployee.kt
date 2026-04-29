package org.athletica.crm.domain.employees

import arrow.core.raise.Raise
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

class AuditEmployee(private val delegate: Employee, private val audit: AuditLog) : Employee by delegate {
    context(ctx: RequestContext, tr: Transaction)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate("employee", id, Json.encodeToString(it))
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun invite(email: EmailAddress, password: String) =
        delegate.invite(email, password).also {
            audit.logUpdate("employee", id, Json.encodeToString(it))
        }

    context(ctx: RequestContext)
    override fun withNew(
        newName: String,
        newPermissions: EmployeePermission,
        newAvatarId: UploadId?,
        newPhoneNo: String?,
        newEmail: EmailAddress?,
        newAllBranchesAccess: Boolean,
        newBranchIds: List<BranchId>,
    ): Employee =
        AuditEmployee(
            delegate.withNew(newName, newPermissions, newAvatarId, newPhoneNo, newEmail, newAllBranchesAccess, newBranchIds),
            audit,
        )
}
