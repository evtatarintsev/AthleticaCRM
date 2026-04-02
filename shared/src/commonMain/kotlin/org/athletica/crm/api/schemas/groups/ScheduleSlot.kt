package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable
import org.athletica.crm.core.DayOfWeek

/**
 * Один слот в расписании группы.
 * [dayOfWeek] — день недели (Пн..Вс).
 * [startAt] и [endAt] — время в формате "HH:MM".
 */
@Serializable
data class ScheduleSlot(
    val dayOfWeek: DayOfWeek,
    val startAt: String,
    val endAt: String,
)
