package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

@Serializable
@JvmInline
value class ClientId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = ClientId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

@Serializable
@JvmInline
value class ClientDocId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = ClientDocId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toClientId(): ClientId = ClientId(this)

fun Uuid.toClientDocId(): ClientDocId = ClientDocId(this)
