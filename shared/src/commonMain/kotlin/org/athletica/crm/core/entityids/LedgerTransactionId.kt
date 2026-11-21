package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор транзакции главной книги. */
@Serializable
@JvmInline
value class LedgerTransactionId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = LedgerTransactionId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toLedgerTransactionId(): LedgerTransactionId = LedgerTransactionId(this)
