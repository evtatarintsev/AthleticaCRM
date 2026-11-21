package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId

/** Запрос на блокировку сотрудника. */
@Serializable
data class BlockEmployeeRequest(
    /** Блокируемый сотрудник. */
    val employeeId: EmployeeId,
)

/** Запрос на разблокировку сотрудника. */
@Serializable
data class UnblockEmployeeRequest(
    /** Разблокируемый сотрудник. */
    val employeeId: EmployeeId,
)
