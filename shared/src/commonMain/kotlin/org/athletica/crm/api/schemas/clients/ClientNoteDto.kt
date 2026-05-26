package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.common.PerformedBy
import org.athletica.crm.core.clientnotes.ClientNoteText
import org.athletica.crm.core.entityids.ClientNoteId
import kotlin.time.Instant

/** Проекция заметки клиента для отображения в UI. */
@Serializable
data class ClientNoteDto(
    val id: ClientNoteId,
    /** Текст заметки. */
    val text: ClientNoteText,
    /** Автор заметки: идентификатор сотрудника и его имя на момент чтения. */
    val author: PerformedBy,
    /** Время создания. */
    val createdAt: Instant,
    /** Время последнего редактирования; null если заметку не редактировали. */
    val updatedAt: Instant?,
)
