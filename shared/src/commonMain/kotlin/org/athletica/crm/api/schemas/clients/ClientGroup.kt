package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ClientGroup(
    val id: Uuid,
    val name: String,
)
