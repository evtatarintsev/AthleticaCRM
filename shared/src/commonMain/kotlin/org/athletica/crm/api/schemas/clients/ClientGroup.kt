package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId

@Serializable
data class ClientGroup(
    val id: GroupId,
    val name: String,
)
