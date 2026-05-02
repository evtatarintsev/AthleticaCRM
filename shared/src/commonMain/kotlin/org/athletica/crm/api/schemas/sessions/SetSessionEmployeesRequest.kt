package org.athletica.crm.api.schemas.sessions

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId

/** Запрос на полную замену списка преподавателей занятия. */
@Serializable
data class SetSessionEmployeesRequest(
    val employeeIds: List<EmployeeId>,
)
