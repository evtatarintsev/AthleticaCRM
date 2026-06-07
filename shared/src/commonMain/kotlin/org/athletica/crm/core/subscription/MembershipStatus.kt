package org.athletica.crm.core.subscription

import kotlinx.serialization.Serializable

/**
 * Статус выданного абонемента, вычисляемый на сервере по дате окончания.
 */
@Serializable
enum class MembershipStatus {
    /** Действует: сегодняшняя дата не позже даты окончания. */
    ACTIVE,

    /** Истёк: дата окончания в прошлом. */
    EXPIRED,
}
