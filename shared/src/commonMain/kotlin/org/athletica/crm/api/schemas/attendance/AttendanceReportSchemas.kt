package org.athletica.crm.api.schemas.attendance

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.schedule.ScheduleGroupSchema
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId

/** Запрос сводки посещаемости группы за период. */
@Serializable
data class GroupAttendanceRequest(
    /** Группа. */
    val groupId: GroupId,
    /** Первый день периода включительно. */
    val from: LocalDate,
    /** Последний день периода включительно. */
    val to: LocalDate,
)

/** Сводка посещаемости группы: проведённые занятия периода и отметки участников по ним. */
@Serializable
data class GroupAttendanceResponse(
    /** Проведённые занятия периода по дате и времени. */
    val sessions: List<AttendanceSessionSchema>,
    /** Участники, отмеченные хотя бы на одном занятии периода, по имени. */
    val participants: List<GroupAttendanceRowSchema>,
)

/** Проведённое занятие в сводке. */
@Serializable
data class AttendanceSessionSchema(
    /** Идентификатор занятия. */
    val id: SessionId,
    /** Дата занятия. */
    val date: LocalDate,
    /** Время начала. */
    val startTime: LocalTime,
)

/** Строка сводки группы: участник и его отметки. */
@Serializable
data class GroupAttendanceRowSchema(
    /** Клиент. */
    val clientId: ClientId,
    /** Имя клиента. */
    val name: String,
    /** Число занятий, на которых участник присутствовал. */
    val presentCount: Int,
    /** Число занятий, на которых участник отсутствовал. */
    val absentCount: Int,
    /** Отметки участника по занятиям периода. */
    val marks: List<AttendanceMarkSchema>,
)

/** Отметка участника на одном занятии. */
@Serializable
data class AttendanceMarkSchema(
    /** Занятие. */
    val sessionId: SessionId,
    /** Присутствие. */
    val presence: AttendancePresence,
    /** Метки отметки. */
    val labels: List<AttendanceLabelSchema>,
)

/** Запрос посещаемости клиента за период. */
@Serializable
data class ClientAttendanceRequest(
    /** Клиент. */
    val clientId: ClientId,
    /** Первый день периода включительно. */
    val from: LocalDate,
    /** Последний день периода включительно. */
    val to: LocalDate,
)

/** Посещаемость клиента по проведённым занятиям всех групп за период. */
@Serializable
data class ClientAttendanceResponse(
    /** Отметки клиента по дате и времени занятия. */
    val items: List<ClientAttendanceItemSchema>,
)

/** Отметка клиента на одном проведённом занятии. */
@Serializable
data class ClientAttendanceItemSchema(
    /** Занятие. */
    val sessionId: SessionId,
    /** Группа занятия. */
    val group: ScheduleGroupSchema,
    /** Дата занятия. */
    val date: LocalDate,
    /** Время начала. */
    val startTime: LocalTime,
    /** Присутствие. */
    val presence: AttendancePresence,
    /** Метки отметки. */
    val labels: List<AttendanceLabelSchema>,
)
