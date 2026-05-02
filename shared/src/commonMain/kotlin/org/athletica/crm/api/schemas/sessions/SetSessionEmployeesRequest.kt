package org.athletica.crm.api.schemas.sessions

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.SessionId

/** Запрос на полную замену списка преподавателей занятия. */
@Serializable
data class SetSessionEmployeesRequest(
    val sessionId: SessionId,
    val employeeIds: List<EmployeeId>,
)
