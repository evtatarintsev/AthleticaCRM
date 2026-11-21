package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор статьи доходов и расходов. */
@Serializable
@JvmInline
value class CashArticleId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = CashArticleId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toCashArticleId(): CashArticleId = CashArticleId(this)
