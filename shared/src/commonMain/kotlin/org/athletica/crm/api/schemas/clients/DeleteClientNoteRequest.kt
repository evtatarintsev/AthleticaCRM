package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientNoteId

/** Запрос на удаление заметки клиента (soft-delete, только автором). */
@Serializable
data class DeleteClientNoteRequest(
    val noteId: ClientNoteId,
)
