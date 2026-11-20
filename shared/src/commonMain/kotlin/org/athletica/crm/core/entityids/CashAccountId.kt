package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор кассы. */
@Serializable
@JvmInline
value class CashAccountId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = CashAccountId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toCashAccountId(): CashAccountId = CashAccountId(this)
