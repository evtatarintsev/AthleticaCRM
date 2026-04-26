package org.athletica.crm.api.schemas.sessions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId

/** Запрос на создание разового занятия вне расписания. */
@Serializable
data class CreateSessionRequest(
    val id: SessionId,
    val groupId: GroupId,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val notes: String? = null,
)
