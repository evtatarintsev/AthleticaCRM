package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.EmployeeId
import org.athletica.crm.core.UploadId

/** Запрос на создание нового сотрудника организации. */
@Serializable
data class CreateEmployeeRequest(
    val id: EmployeeId,
    val name: String,
    val phoneNo: String? = null,
    val email: String? = null,
    val avatarId: UploadId? = null,
)
