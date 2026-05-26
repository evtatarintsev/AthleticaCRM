package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.entityids.ClientNoteId

/** Запрос на изменение текста существующей заметки клиента. */
@Serializable
data class EditClientNoteRequest(
    val noteId: ClientNoteId,
    val text: ClientNoteText,
)
