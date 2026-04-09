package org.athletica.crm.api.schemas.employees

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
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
    val id: Uuid,
    val name: String,
    val avatarId: Uuid? = null,
    val isOwner: Boolean,
    val isActive: Boolean,
    val joinedAt: Instant,
    val roles: List<EmployeeRole>,
)

/** Ответ на запрос списка сотрудников. */
@Serializable
data class EmployeeListResponse(
    val employees: List<EmployeeListItem>,
    val total: UInt,
)
