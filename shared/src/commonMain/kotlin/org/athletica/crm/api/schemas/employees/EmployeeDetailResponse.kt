package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.permissions.UserPermission
import kotlin.time.Instant

/** Полные данные сотрудника. */
@Serializable
data class EmployeeDetailResponse(
    val id: EmployeeId,
    val name: String,
    val avatarId: UploadId? = null,
    val isOwner: Boolean,
    /** Статус доступа сотрудника. */
    val status: EmployeeAccessStatus,
    /** Момент последней активности; `null` — сотрудник ещё не входил. */
    val lastSeenAt: Instant? = null,
    /** Сотрудник «в сети»: активность не старше 5 минут на момент запроса. */
    val online: Boolean = false,
    val joinedAt: Instant,
    val roles: List<EmployeeRole>,
    val phoneNo: String? = null,
    val email: String? = null,
    val grantedPermissions: Set<UserPermission> = emptySet(),
    val revokedPermissions: Set<UserPermission> = emptySet(),
    /** true — доступ ко всем филиалам. */
    val allBranchesAccess: Boolean = true,
    /** Конкретные филиалы; актуально только когда [allBranchesAccess] = false. */
    val branchIds: List<BranchId> = emptyList(),
)
