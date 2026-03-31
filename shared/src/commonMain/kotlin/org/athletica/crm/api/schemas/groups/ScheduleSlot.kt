package org.athletica.crm.api.schemas.groups

import kotlinx.serialization.Serializable

/**
 * Один слот в расписании группы.
 * [dayOfWeek] — день недели: 0=Пн, 1=Вт, ..., 6=Вс (совместимо с ISO-8601).
 * [startAt] и [endAt] — время в формате "HH:MM".
 */
@Serializable
data class ScheduleSlot(
    val dayOfWeek: Int,
    val startAt: String,
    val endAt: String,
)
