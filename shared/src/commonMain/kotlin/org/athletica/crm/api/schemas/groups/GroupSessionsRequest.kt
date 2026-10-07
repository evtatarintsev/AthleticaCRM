package org.athletica.crm.api.schemas.groups

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.schedule.ScheduleCoachSchema
import org.athletica.crm.api.schemas.schedule.ScheduleHallSchema
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.sessions.SessionStatus

/** Запрос занятий группы за период для блока «Занятия» карточки группы. */
@Serializable
data class GroupSessionsRequest(
    /** Группа. */
    val groupId: GroupId,
    /** Первый день периода включительно. */
    val from: LocalDate,
    /** Последний день периода включительно. */
    val to: LocalDate,
)

/** Занятия группы за период и закреплённые последнее и ближайшее занятия. */
@Serializable
data class GroupSessionsResponse(
    /** Занятия периода во всех статусах, по дате и времени начала. */
    val sessions: List<GroupSessionSchema>,
    /** Последнее неотменённое занятие, начало которого наступило; `null` — такого нет. */
    val last: GroupSessionSchema?,
    /** Ближайшее запланированное занятие, начало которого не наступило; `null` — такого нет. */
    val next: GroupSessionSchema?,
)

/** Строка занятия в блоке «Занятия» карточки группы. */
@Serializable
data class GroupSessionSchema(
    /** Идентификатор занятия. */
    val id: SessionId,
    /** Дата занятия. */
    val date: LocalDate,
    /** Время начала. */
    val startTime: LocalTime,
    /** Время окончания. */
    val endTime: LocalTime,
    /** Зал занятия. */
    val hall: ScheduleHallSchema,
    /** Тренеры занятия (с учётом переопределения состава на занятии), по имени. */
    val coaches: List<ScheduleCoachSchema>,
    /** Состав тренеров изменён вручную на этом занятии. */
    val coachesOverridden: Boolean,
    /** Статус занятия. */
    val status: SessionStatus,
    /** Дата, на которую занятие предписывало расписание, если оно перенесено на другой день; иначе `null`. */
    val rescheduledFrom: LocalDate?,
    /** Занятие создано вручную, вне расписания группы. */
    val isManual: Boolean,
    /** Посещаемость проведённого занятия; `null` для запланированного и отменённого. */
    val attendance: GroupSessionAttendanceSchema?,
)

/** Посещаемость проведённого занятия. */
@Serializable
data class GroupSessionAttendanceSchema(
    /** Число пришедших участников. */
    val present: Int,
    /** Число участников в составе занятия на момент проведения. */
    val total: Int,
)
