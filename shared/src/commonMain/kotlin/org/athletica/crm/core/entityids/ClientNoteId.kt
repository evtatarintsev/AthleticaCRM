package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Уникальный идентификатор заметки о клиенте. */
@Serializable
@JvmInline
value class ClientNoteId(override val value: Uuid) : EntityId {
    companion object {
        /** Создаёт новый уникальный идентификатор заметки (UUID v7). */
        fun new() = ClientNoteId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Конвертирует [Uuid] в [ClientNoteId]. */
fun Uuid.toClientNoteId(): ClientNoteId = ClientNoteId(this)
