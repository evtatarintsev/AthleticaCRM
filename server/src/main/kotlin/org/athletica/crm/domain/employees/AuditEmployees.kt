package org.athletica.crm.domain.employees

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.storage.Transaction

class AuditEmployees(private val delegate: Employees, private val audit: AuditLog) : Employees by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: EmployeeId) = AuditEmployee(delegate.byId(id), audit)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list() = delegate.list().map { AuditEmployee(it, audit) }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: EmployeeId,
        name: String,
        phoneNo: String?,
        email: EmailAddress?,
        avatarId: UploadId?,
        permissions: EmployeePermission,
        allBranchesAccess: Boolean,
        branchIds: List<BranchId>,
    ) = delegate.new(id, name, phoneNo, email, avatarId, permissions, allBranchesAccess, branchIds)
        .also {
            val data = NewEmployeeAuditRecord(it.id, it.name, it.phoneNo, it.email)
            audit.logCreate("employee", id, Json.encodeToString(data))
        }
}

@Serializable
data class NewEmployeeAuditRecord(
    val id: EmployeeId,
    val name: String,
    val phoneNo: String?,
    val email: EmailAddress?,
)
