package org.athletica.crm.api.schemas.schedule

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.sessions.SessionStatus

/** Занятия периода, готовые к отрисовке без обращения к справочникам. */
@Serializable
data class ScheduleListResponse(
    /** Занятия, отсортированные по дате и времени начала. */
    val sessions: List<ScheduleSessionSchema>,
)

/** Карточка занятия в расписании. */
@Serializable
data class ScheduleSessionSchema(
    /** Идентификатор занятия. */
    val id: SessionId,
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
    /** Тренеры занятия (с учётом переопределения состава на занятии), по имени. */
    val coaches: List<ScheduleCoachSchema>,
    /** Дисциплины группы занятия, по названию. */
    val disciplines: List<ScheduleDisciplineSchema>,
    /** Статус занятия. */
    val status: SessionStatus,
    /** Ключ палитры карточки. */
    val colorKey: SessionColorKey,
)

/** Группа занятия в расписании. */
@Serializable
data class ScheduleGroupSchema(
    /** Идентификатор группы. */
    val id: GroupId,
    /** Название группы. */
    val name: String,
)

/** Зал занятия в расписании. */
@Serializable
data class ScheduleHallSchema(
    /** Идентификатор зала. */
    val id: HallId,
    /** Название зала. */
    val name: String,
)

/** Тренер занятия в расписании. */
@Serializable
data class ScheduleCoachSchema(
    /** Идентификатор сотрудника. */
    val id: EmployeeId,
    /** Имя сотрудника. */
    val name: String,
)

/** Дисциплина занятия в расписании. */
@Serializable
data class ScheduleDisciplineSchema(
    /** Идентификатор дисциплины. */
    val id: DisciplineId,
    /** Название дисциплины. */
    val name: String,
)
