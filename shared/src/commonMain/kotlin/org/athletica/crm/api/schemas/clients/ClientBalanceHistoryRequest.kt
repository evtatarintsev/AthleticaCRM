package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId

/** Запрос истории баланса клиента по [id]. */
@Serializable
data class ClientBalanceHistoryRequest(
    val id: ClientId,
)
