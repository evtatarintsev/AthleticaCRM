package org.athletica.crm.api.schemas.tariffs

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.TariffPlanId

/**
 * Запрос на архивирование/восстановление тарифного плана.
 */
@Serializable
data class ArchiveTariffPlanRequest(
    /** Идентификатор плана. */
    val id: TariffPlanId,
    /** `true` — отправить в архив, `false` — восстановить. */
    val archived: Boolean,
)
