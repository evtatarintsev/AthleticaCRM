package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.permissions.Permission
import kotlin.time.Instant

/** Полные данные сотрудника. */
@Serializable
data class EmployeeDetailResponse(
    val id: EmployeeId,
    val name: String,
    val avatarId: UploadId? = null,
    val isOwner: Boolean,
    val isActive: Boolean,
    val joinedAt: Instant,
    val roles: List<EmployeeRole>,
    val phoneNo: String? = null,
    val email: String? = null,
    val grantedPermissions: Set<Permission> = emptySet(),
    val revokedPermissions: Set<Permission> = emptySet(),
)
