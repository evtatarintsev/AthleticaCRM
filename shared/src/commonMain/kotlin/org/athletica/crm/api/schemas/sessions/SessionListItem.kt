package org.athletica.crm.api.schemas.sessions

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId

/** Элемент списка занятий для отображения в календаре или расписании. */
@Serializable
data class SessionListItem(
    val id: SessionId,
    val groupId: GroupId,
    val groupName: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val hallId: HallId,
    val status: String,
    val isManual: Boolean,
    val isRescheduled: Boolean,
    val notes: String?,
    val employeeIds: List<EmployeeId> = emptyList(),
    val isEmployeeAssignmentOverridden: Boolean = false,
)
