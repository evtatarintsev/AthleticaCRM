package org.athletica.crm.core

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

fun Uuid.toClientId(): ClientId = ClientId(this)
