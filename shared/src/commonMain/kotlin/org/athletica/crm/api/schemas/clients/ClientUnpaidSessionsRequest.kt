package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId

/** Запрос всех неоплаченных посещений клиента в организации. */
@Serializable
data class ClientUnpaidSessionsRequest(val clientId: ClientId)
