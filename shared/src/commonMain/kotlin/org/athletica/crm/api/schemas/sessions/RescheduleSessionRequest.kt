package org.athletica.crm.api.schemas.sessions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.HallId

/** Запрос на перенос занятия на другой день или время. */
@Serializable
data class RescheduleSessionRequest(
    val newDate: LocalDate,
    val newStartTime: LocalTime,
    val newEndTime: LocalTime,
    val newHallId: HallId,
)
