package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Роль сотрудника (краткое представление). */
@Serializable
data class EmployeeRole(
    val id: Uuid,
    val name: String,
)

/** Элемент списка сотрудников. */
@Serializable
data class EmployeeListItem(
    val id: EmployeeId,
    val name: String,
    val avatarId: UploadId? = null,
    val isOwner: Boolean,
    val isActive: Boolean,
    val joinedAt: Instant,
    val roles: List<EmployeeRole>,
    val phoneNo: String? = null,
    val email: String? = null,
)

/** Ответ на запрос списка сотрудников. */
@Serializable
data class EmployeeListResponse(
    val employees: List<EmployeeListItem>,
    val total: UInt,
)
