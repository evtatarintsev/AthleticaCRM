package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable

/** Список заметок клиента. */
@Serializable
data class ClientNotesListResponse(
    val notes: List<ClientNoteSchema>,
)
