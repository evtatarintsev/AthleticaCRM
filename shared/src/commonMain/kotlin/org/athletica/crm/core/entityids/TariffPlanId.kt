package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Идентификатор тарифного плана абонемента.
 */
@Serializable
@JvmInline
value class TariffPlanId(override val value: Uuid) : EntityId {
    companion object {
        fun new() = TariffPlanId(Uuid.generateV7())
    }

    override fun toString() = value.toString()
}

fun Uuid.toTariffPlanId(): TariffPlanId = TariffPlanId(this)
