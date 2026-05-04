package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId

/** Запрос на получение детальной информации о клиенте по [id]. */
@Serializable
data class ClientDetailRequest(val id: ClientId)
