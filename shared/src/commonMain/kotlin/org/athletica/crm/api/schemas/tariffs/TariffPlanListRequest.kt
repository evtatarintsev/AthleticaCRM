package org.athletica.crm.api.schemas.tariffs

import kotlinx.serialization.Serializable

/**
 * Запрос списка тарифных планов.
 */
@Serializable
data class TariffPlanListRequest(
    /** Включать ли архивные планы. По умолчанию — только активные. */
    val includeArchived: Boolean = false,
)

/**
 * Ответ со списком тарифных планов.
 */
@Serializable
data class TariffPlanListResponse(
    /** Тарифные планы организации. */
    val tariffs: List<TariffPlanSchema>,
)
