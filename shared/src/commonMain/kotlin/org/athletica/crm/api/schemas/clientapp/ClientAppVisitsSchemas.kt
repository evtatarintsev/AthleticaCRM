package org.athletica.crm.api.schemas.clientapp

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId

/**
 * История посещений профиля: клиент [clientId] организации [orgId] за месяц, в который попадает [monthOf];
 * `null` — текущий месяц организации.
 */
@Serializable
data class ClientAppVisitsRequest(
    val orgId: OrgId,
    val clientId: ClientId,
    val monthOf: LocalDate? = null,
)

/**
 * Проведённые занятия клиента [visits] за месяц с первого числа [monthStart] по дате и времени
 * и число посещений месяца [presentCount] — для главной.
 */
@Serializable
data class ClientAppVisitsResponse(
    val monthStart: LocalDate,
    val visits: List<ClientAppVisitSchema>,
    val presentCount: Int,
)

/**
 * Проведённое занятие [sessionId] [date] в [startTime] группы [groupName] с отметкой клиента [presence]:
 * «был» или «не был», без меток и заметок сотрудников.
 */
@Serializable
data class ClientAppVisitSchema(
    val sessionId: SessionId,
    val date: LocalDate,
    val startTime: LocalTime,
    val groupName: String?,
    val presence: AttendancePresence,
)
