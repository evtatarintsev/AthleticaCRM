package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.UploadId
import kotlin.uuid.Uuid

/** Запрос на создание нового сотрудника организации. */
@Serializable
data class CreateEmployeeRequest(
    val id: Uuid,
    val name: String,
    val phoneNo: String? = null,
    val email: String? = null,
    val avatarId: UploadId? = null,
)
