package org.athletica.crm.domain.employees

import arrow.core.raise.Raise
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface Employee {
    val id: EmployeeId
    val userId: UserId?
    val name: String
    val avatarId: UploadId?
    val isOwner: Boolean
    val isActive: Boolean
    val joinedAt: Instant
    val roles: List<EmployeeRole>
    val phoneNo: String?
    val email: EmailAddress?

    context(ctx: RequestContext, tr: Transaction)
    suspend fun save()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun invite(email: EmailAddress, password: String)
}

data class EmployeeRole(
    val id: Uuid,
    val name: String,
)
