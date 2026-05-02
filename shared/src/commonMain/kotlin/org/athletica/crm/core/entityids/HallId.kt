package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class HallId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = HallId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toHallId(): HallId = HallId(this)
