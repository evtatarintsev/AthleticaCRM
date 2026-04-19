package org.athletica.crm.api.schemas.employees

import kotlinx.serialization.Serializable
import org.athletica.crm.core.permissions.Permission
import kotlin.uuid.Uuid

@Serializable
data class RoleListResponse(
    val roles: List<RoleItem>,
)

@Serializable
data class RoleItem(
    val id: Uuid,
    val name: String,
    val permissions: Set<Permission>,
)
