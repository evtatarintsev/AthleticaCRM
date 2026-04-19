package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class DisciplineId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = DisciplineId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toDisciplineId(): DisciplineId = DisciplineId(this)
