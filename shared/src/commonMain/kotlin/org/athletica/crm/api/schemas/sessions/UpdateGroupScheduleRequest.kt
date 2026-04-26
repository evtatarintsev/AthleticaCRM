package org.athletica.crm.api.schemas.sessions

import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.groups.ScheduleSlot

/** Запрос на обновление расписания группы. */
@Serializable
data class UpdateGroupScheduleRequest(
    val schedule: List<ScheduleSlot>,
)
