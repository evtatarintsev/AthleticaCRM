package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.entityids.ClientId

/** Запрос на добавление новой заметки клиенту. */
@Serializable
data class AddClientNoteRequest(
    val clientId: ClientId,
    val text: ClientNoteText,
)
