package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable

/** Параметры запроса списка клиентов с поддержкой пагинации. */
@Serializable
data class ClientListRequest(
    /** Максимальное количество клиентов на странице. */
    val limit: Int = 10,
    /** Смещение от начала списка. */
    val offset: Int = 0,
)
