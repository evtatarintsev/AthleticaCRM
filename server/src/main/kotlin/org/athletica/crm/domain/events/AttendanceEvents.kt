package org.athletica.crm.domain.events

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.attendance.ParticipationKind
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId

/** Отметка участника в событии: присутствие (`null` — не отмечен) и метки. */
@Serializable
data class AttendanceMarkSnapshot(
    /** Присутствие; `null` — участник не отмечен. */
    val presence: AttendancePresence?,
    /** Метки отметки. */
    val labelIds: List<AttendanceLabelId>,
)

/** Неотмеченному участнику занятия поставлена отметка. */
@Serializable
@SerialName("AttendanceMarked")
data class AttendanceMarked(
    /** Занятие. */
    val sessionId: SessionId,
    /** Участник. */
    val clientId: ClientId,
    /** Поставленная отметка. */
    val mark: AttendanceMarkSnapshot,
    /** Сотрудник, поставивший отметку. */
    val markedBy: EmployeeId,
) : DomainEvent

/**
 * Ранее проставленная отметка изменена — в открытом журнале или после проведения занятия.
 * Несёт прежнее значение, чтобы подписчик посчитал дельту, не читая историю.
 */
@Serializable
@SerialName("AttendanceChanged")
data class AttendanceChanged(
    /** Занятие. */
    val sessionId: SessionId,
    /** Участник. */
    val clientId: ClientId,
    /** Отметка до изменения. */
    val previous: AttendanceMarkSnapshot,
    /** Отметка после изменения. */
    val current: AttendanceMarkSnapshot,
    /** Сотрудник, изменивший отметку. */
    val changedBy: EmployeeId,
) : DomainEvent

/** Участник проведённого занятия с итоговой отметкой. */
@Serializable
data class CompletedParticipant(
    /** Клиент. */
    val clientId: ClientId,
    /** Вид участия. */
    val kind: ParticipationKind,
    /** Итоговое присутствие. */
    val presence: AttendancePresence,
    /** Итоговые метки. */
    val labelIds: List<AttendanceLabelId>,
)

/**
 * Занятие проведено, журнал закрыт. Несёт полный итоговый состав, чтобы подписчик
 * (будущее списание абонементов) не обращался к журналу.
 */
@Serializable
@SerialName("SessionCompleted")
data class SessionCompleted(
    /** Занятие. */
    val sessionId: SessionId,
    /** Группа занятия. */
    val groupId: GroupId,
    /** Дата занятия. */
    val date: LocalDate,
    /** Итоговый состав с отметками. */
    val participants: List<CompletedParticipant>,
    /** Сотрудник, проведший занятие. */
    val completedBy: EmployeeId,
) : DomainEvent

/** Занятие отменено; его отметки перестают учитываться. */
@Serializable
@SerialName("SessionCancelled")
data class SessionCancelled(
    /** Занятие. */
    val sessionId: SessionId,
    /** Группа занятия. */
    val groupId: GroupId,
    /** Дата занятия. */
    val date: LocalDate,
    /** Сотрудник, отменивший занятие. */
    val cancelledBy: EmployeeId,
) : DomainEvent
