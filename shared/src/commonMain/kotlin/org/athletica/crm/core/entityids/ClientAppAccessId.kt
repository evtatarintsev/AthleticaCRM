package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор доступа к кабинету клиента. */
@Serializable
@JvmInline
value class ClientAppAccessId(override val value: Uuid) : EntityId {
    companion object {
        /** Новый идентификатор доступа (UUID v7). */
        fun new() = ClientAppAccessId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Оборачивает [Uuid] в [ClientAppAccessId]. */
fun Uuid.toClientAppAccessId(): ClientAppAccessId = ClientAppAccessId(this)
