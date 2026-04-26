package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class SessionId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = SessionId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toSessionId(): SessionId = SessionId(this)
