package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class ClientListItem(
    val id: Uuid,
    val name: String,
)

@Serializable
data class ClientListResponse(
    val clients: List<ClientListItem>,
    val total: UInt,
)
