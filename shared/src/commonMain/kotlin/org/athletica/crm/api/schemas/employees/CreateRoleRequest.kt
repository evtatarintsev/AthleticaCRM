package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.permissions.UserPermission
import kotlin.uuid.Uuid

/** Запрос на создание новой роли организации. */
@Serializable
data class CreateRoleRequest(
    val id: Uuid,
    val name: String,
    val permissions: Set<UserPermission>,
)
