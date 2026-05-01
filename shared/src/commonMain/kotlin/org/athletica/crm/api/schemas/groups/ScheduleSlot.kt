package org.athletica.crm.api.schemas.groups

import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.DayOfWeek
import org.athletica.crm.core.entityids.HallId

/**
 * Один слот в расписании группы.
 * [dayOfWeek] — день недели (Пн..Вс).
 * [startAt] и [endAt] — время в формате "HH:MM".
 */
@Serializable
data class ScheduleSlot(
    val dayOfWeek: DayOfWeek,
    val startAt: LocalTime,
    val endAt: LocalTime,
    val hallId: HallId,
)
