package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.EmailAddress
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId

/** Запрос на создание нового сотрудника организации. */
@Serializable
data class CreateEmployeeRequest(
    val id: EmployeeId,
    val name: String,
    val phoneNo: String? = null,
    val email: EmailAddress? = null,
    val avatarId: UploadId? = null,
)
