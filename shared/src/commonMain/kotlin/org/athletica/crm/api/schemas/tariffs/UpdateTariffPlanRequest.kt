package org.athletica.crm.api.schemas.tariffs

import kotlinx.serialization.Serializable
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.PositiveInt
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit

/**
 * Запрос на изменение тарифного плана.
 */
@Serializable
data class UpdateTariffPlanRequest(
    /** Идентификатор изменяемого плана. */
    val id: TariffPlanId,
    /** Отображаемое название плана. */
    val name: NonBlankName,
    /** Количество занятий; `null` — безлимитный план. */
    val sessions: PositiveInt?,
    /** Числовое значение срока действия. */
    val durationValue: PositiveInt,
    /** Единица измерения срока действия. */
    val durationUnit: DurationUnit,
    /** Стоимость плана. */
    val price: Money,
)
