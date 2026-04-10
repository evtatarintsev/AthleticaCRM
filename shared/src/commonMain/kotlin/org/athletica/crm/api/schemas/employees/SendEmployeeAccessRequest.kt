package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class SendEmployeeAccessRequest(
    val employeeId: Uuid,
    val password: String,
)
