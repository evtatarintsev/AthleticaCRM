package org.athletica.crm.core

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class DisciplineId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = DisciplineId(Uuid.generateV7())
    }
}

fun Uuid.toDisciplineId(): DisciplineId = DisciplineId(this)
