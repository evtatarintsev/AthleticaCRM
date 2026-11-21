package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор продажи. */
@Serializable
@JvmInline
value class SaleId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = SaleId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toSaleId(): SaleId = SaleId(this)
