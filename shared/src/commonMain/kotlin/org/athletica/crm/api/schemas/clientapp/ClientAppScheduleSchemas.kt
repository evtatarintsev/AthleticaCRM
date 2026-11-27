package org.athletica.crm.api.schemas.clientapp

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.sessions.SessionStatus
import kotlin.time.Instant

/**
 * Расписание профиля: клиент [clientId] организации [orgId] за неделю, в которую попадает [weekOf];
 * `null` — текущая неделя организации.
 */
@Serializable
data class ClientAppScheduleRequest(
    val orgId: OrgId,
    val clientId: ClientId,
    val weekOf: LocalDate? = null,
)

/**
 * Занятия клиента за неделю с понедельника [weekStart] по дате и времени [sessions], «сегодня»
 * организации [today] и ближайшее ещё не начавшееся запланированное занятие [next] — для главной.
 */
@Serializable
data class ClientAppScheduleResponse(
    val weekStart: LocalDate,
    val today: LocalDate,
    val sessions: List<ClientAppScheduleSessionSchema>,
    val next: ClientAppScheduleSessionSchema?,
)

/**
 * Занятие [sessionId] клиента [date] с [startTime] до [endTime] группы [groupName] в зале [hallName]
 * с тренерами [coaches] и статусом [status]. [movedFrom] — прежние дата и время перенесённого занятия,
 * [hasStarted] — занятие уже началось, [absenceNoticeAt] — когда предупредили «Не придём».
 */
@Serializable
data class ClientAppScheduleSessionSchema(
    val sessionId: SessionId,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val groupName: String?,
    val hallName: String,
    val coaches: List<String>,
    val status: SessionStatus,
    val movedFrom: ClientAppMovedFromSchema?,
    val hasStarted: Boolean,
    val absenceNoticeAt: Instant?,
)

/** Дата [date] и время начала [startTime], на которые занятие стояло до переноса. */
@Serializable
data class ClientAppMovedFromSchema(
    val date: LocalDate,
    val startTime: LocalTime,
)

/** «Не придём» или его отмена: клиент [clientId] организации [orgId] на занятии [sessionId]. */
@Serializable
data class ClientAppAbsenceNoticeRequest(
    val orgId: OrgId,
    val clientId: ClientId,
    val sessionId: SessionId,
)
