package org.athletica.crm.core.subscription

import kotlinx.serialization.Serializable

/**
 * Единица измерения срока действия абонемента/тарифа.
 */
@Serializable
enum class DurationUnit {
    /** Дни. */
    DAYS,

    /** Месяцы. */
    MONTHS,
}
