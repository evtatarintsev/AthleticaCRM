package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор сеанса кабинета клиента. */
@Serializable
@JvmInline
value class ClientAppSessionId(override val value: Uuid) : EntityId {
    companion object {
        /** Новый идентификатор сеанса (UUID v7). */
        fun new() = ClientAppSessionId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Оборачивает [Uuid] в [ClientAppSessionId]. */
fun Uuid.toClientAppSessionId(): ClientAppSessionId = ClientAppSessionId(this)
