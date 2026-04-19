package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.entityids.EmployeeId

@Serializable
data class SendEmployeeAccessRequest(
    val employeeId: EmployeeId,
    val email: EmailAddress,
    val password: String,
)
