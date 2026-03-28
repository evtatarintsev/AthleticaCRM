package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable

@Serializable
data class ClientListRequest(
    val limit: Int = 10,
    val offset: Int = 0,
)
