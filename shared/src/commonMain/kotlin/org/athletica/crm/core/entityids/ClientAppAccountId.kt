package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор аккаунта кабинета клиента. */
@Serializable
@JvmInline
value class ClientAppAccountId(override val value: Uuid) : EntityId {
    companion object {
        /** Новый идентификатор аккаунта (UUID v7). */
        fun new() = ClientAppAccountId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Оборачивает [Uuid] в [ClientAppAccountId]. */
fun Uuid.toClientAppAccountId(): ClientAppAccountId = ClientAppAccountId(this)
