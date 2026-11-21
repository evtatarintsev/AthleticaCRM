package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор позиции продажи. */
@Serializable
@JvmInline
value class SaleItemId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = SaleItemId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toSaleItemId(): SaleItemId = SaleItemId(this)
