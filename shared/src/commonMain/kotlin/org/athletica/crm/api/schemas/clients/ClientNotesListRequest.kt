package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId

/** Запрос на чтение списка не удалённых заметок клиента в порядке новых сверху. */
@Serializable
data class ClientNotesListRequest(
    val clientId: ClientId,
)
