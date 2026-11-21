package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор сессии входа пользователя (claim `sid` в токенах). */
@Serializable
@JvmInline
value class AuthSessionId(override val value: Uuid) : EntityId {
    companion object {
        /** Новый идентификатор сессии. */
        fun new() = AuthSessionId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

/** Оборачивает [Uuid] в [AuthSessionId]. */
fun Uuid.toAuthSessionId(): AuthSessionId = AuthSessionId(this)
