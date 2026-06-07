package org.athletica.crm.api.schemas.tariffs

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit

/**
 * Запрос на создание тарифного плана.
 */
@Serializable
data class CreateTariffPlanRequest(
    /** Идентификатор плана (генерируется клиентом). */
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
)
