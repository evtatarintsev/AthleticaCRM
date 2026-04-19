package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.permissions.Permission
import kotlin.uuid.Uuid

/** Запрос на обновление роли организации. */
@Serializable
data class UpdateRoleRequest(
    val id: Uuid,
    val name: String,
    val permissions: Set<Permission>,
)
