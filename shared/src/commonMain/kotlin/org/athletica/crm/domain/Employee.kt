package org.athletica.crm.domain

import kotlinx.serialization.Serializable
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.PhoneNo
import org.athletica.crm.core.UploadId
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class EmployeeRole(
    val id: Uuid,
    val name: String,
)

@Serializable
data class Employee(
    val id: EmployeeId,
    val name: String,
    val avatarId: UploadId? = null,
    val isOwner: Boolean,
    val isActive: Boolean,
    val joinedAt: Instant,
    val roles: List<EmployeeRole>,
    val phoneNo: PhoneNo? = null,
    val email: EmailAddress? = null,
)
