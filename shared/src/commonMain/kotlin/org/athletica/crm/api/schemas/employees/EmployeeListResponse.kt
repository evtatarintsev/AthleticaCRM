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
)

/** Ответ на запрос списка сотрудников. */
@Serializable
data class EmployeeListResponse(
    val employees: List<EmployeeListItem>,
    val total: UInt,
)
