package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId

@Serializable
data class EmployeeDetailRequest(
    val id: EmployeeId,
)
