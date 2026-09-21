package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор версии слота расписания. */
@Serializable
@JvmInline
value class SlotId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = SlotId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toSlotId(): SlotId = SlotId(this)
