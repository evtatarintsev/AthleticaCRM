package org.athletica.crm.api.schemas.attendance

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.schedule.ScheduleCoachSchema
import org.athletica.crm.api.schemas.schedule.ScheduleGroupSchema
import org.athletica.crm.api.schemas.schedule.ScheduleHallSchema
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.attendance.ParticipationKind
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.sessions.SessionStatus

/** Запрос журнала посещаемости занятия. */
@Serializable
data class SessionJournalRequest(
    /** Занятие, журнал которого запрашивается. */
    val sessionId: SessionId,
)

/** Карточка занятия с журналом посещаемости. */
@Serializable
data class SessionJournalResponse(
    /** Идентификатор занятия. */
    val sessionId: SessionId,
    /** Группа занятия. */
    val group: ScheduleGroupSchema,
    /** Дата занятия. */
    val date: LocalDate,
    /** Время начала. */
    val startTime: LocalTime,
    /** Время окончания. */
    val endTime: LocalTime,
    /** Зал занятия. */
    val hall: ScheduleHallSchema,
    /** Тренеры занятия, по имени. */
    val coaches: List<ScheduleCoachSchema>,
    /** Статус занятия. */
    val status: SessionStatus,
    /** Время начала занятия уже наступило (в часовом поясе организации). */
    val hasStarted: Boolean,
    /** Состав занятия, по имени клиента. */
    val participants: List<JournalParticipantSchema>,
)

/** Участник занятия с его отметкой. */
@Serializable
data class JournalParticipantSchema(
    /** Клиент-участник. */
    val clientId: ClientId,
    /** Имя клиента. */
    val name: String,
    /** Вид участия. */
    val kind: ParticipationKind,
    /** Присутствие; `null` — участник не отмечен. */
    val presence: AttendancePresence?,
    /** Метки отметки, в порядке справочника; включая архивные. */
    val labels: List<AttendanceLabelSchema>,
)

/** Запрос на установку отметки участнику занятия. */
@Serializable
data class MarkAttendanceRequest(
    /** Занятие. */
    val sessionId: SessionId,
    /** Участник занятия. */
    val clientId: ClientId,
    /** Присутствие; `null` снимает отметку (только в открытом журнале). */
    val presence: AttendancePresence?,
    /** Метки отметки; для отсутствия обязательна хотя бы одна. */
    val labelIds: List<AttendanceLabelId> = emptyList(),
)

/** Запрос на массовую отметку неотмеченных участников как отсутствующих. */
@Serializable
data class MarkRemainingAbsentRequest(
    /** Занятие. */
    val sessionId: SessionId,
    /** Метка отсутствия, назначаемая каждому отмеченному. */
    val labelId: AttendanceLabelId,
)

/** Запрос на добавление или удаление разового участника занятия. */
@Serializable
data class JournalParticipantRequest(
    /** Занятие. */
    val sessionId: SessionId,
    /** Клиент. */
    val clientId: ClientId,
)

/** Запрос на проведение (закрытие журнала) занятия. */
@Serializable
data class CompleteSessionRequest(
    /** Проводимое занятие. */
    val sessionId: SessionId,
)
