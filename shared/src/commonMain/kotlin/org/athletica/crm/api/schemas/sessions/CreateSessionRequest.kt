package org.athletica.crm.api.schemas.sessions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId

/** Запрос на создание разового занятия вне расписания. */
@Serializable
data class CreateSessionRequest(
    val id: SessionId,
    val groupId: GroupId,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val hallId: HallId,
    /** Тренеры занятия; пустой список — занятие без тренеров, тренеры группы не подставляются. */
    val employeeIds: List<EmployeeId>,
    val notes: String? = null,
)
