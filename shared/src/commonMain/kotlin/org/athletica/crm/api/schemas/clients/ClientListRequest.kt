package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable

/** Параметры запроса списка клиентов с поддержкой пагинации. */
@Serializable
data class ClientListRequest(
    val name: String? = null,
)
