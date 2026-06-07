package org.athletica.crm.api.schemas.tariffs

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit

/**
 * Тарифный план абонемента в ответах API.
 */
@Serializable
data class TariffPlanSchema(
    /** Идентификатор плана. */
    val id: TariffPlanId,
    /** Отображаемое название плана. */
    val name: String,
    /** Количество занятий; `null` — безлимитный план. */
    val sessions: Int?,
    /** Числовое значение срока действия. */
    val durationValue: Int,
    /** Единица измерения срока действия. */
    val durationUnit: DurationUnit,
    /** Стоимость плана. */
    val price: Money,
    /** Признак архивного (скрытого из выдачи) плана. */
    val archived: Boolean,
)
