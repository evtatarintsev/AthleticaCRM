package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/** Запрос на создание нового сотрудника организации. */
@Serializable
data class CreateEmployeeRequest(
    val id: Uuid,
    val name: String,
    val phoneNo: String? = null,
    val email: String? = null,
    val avatarId: Uuid? = null,
)
