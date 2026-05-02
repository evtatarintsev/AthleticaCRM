package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId

/**
 * Запрос на установку преподавателей группы.
 * Полностью заменяет текущий список преподавателей группы.
 */
@Serializable
data class SetGroupEmployeesRequest(
    /** Идентификатор группы. */
    val groupId: GroupId,
    /** Новый список преподавателей группы. */
    val employeeIds: List<EmployeeId>,
)
