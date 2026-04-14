package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.EmployeeId

@Serializable
data class SendEmployeeAccessRequest(
    val employeeId: EmployeeId,
    val password: String,
)
