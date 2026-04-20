package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.permissions.Permission
import kotlin.uuid.Uuid

/** Запрос на обновление данных сотрудника организации. */
@Serializable
data class UpdateEmployeeRequest(
    val id: EmployeeId,
    val name: String,
    val phoneNo: String? = null,
    val email: EmailAddress? = null,
    val avatarId: UploadId? = null,
    val roleIds: List<Uuid> = emptyList(),
    val grantedPermissions: Set<Permission> = emptySet(),
    val revokedPermissions: Set<Permission> = emptySet(),
)
