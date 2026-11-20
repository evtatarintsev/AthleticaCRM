package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор счёта главной книги. */
@Serializable
@JvmInline
value class LedgerAccountId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = LedgerAccountId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toLedgerAccountId(): LedgerAccountId = LedgerAccountId(this)
