package org.athletica.crm.domain.employees

import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.UploadId
import org.athletica.crm.core.UserId
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
    val email: String?

    context(tr: Transaction)
    suspend fun save()

    fun withUserId(userId: UserId): Employee

    fun activate(): Employee
}

data class EmployeeRole(
    val id: Uuid,
    val name: String,
)
