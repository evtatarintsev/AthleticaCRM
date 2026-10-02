package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.storage.Transaction

/**
 * Журнал посещаемости занятия: состав и отметки участников.
 *
 * Снимок на момент загрузки: после команды журнал загружается заново.
 * Журнал запланированного занятия открыт; проведённого — закрыт, но отметки в нём правятся
 * без переоткрытия; отменённого — недоступен для изменений.
 */
interface SessionJournal {
    /** Занятие журнала. */
    val sessionId: SessionId

    /** Группа занятия. */
    val groupId: GroupId

    /** Дата занятия. */
    val date: LocalDate

    /** Статус занятия. */
    val status: SessionStatus

    /** Состав занятия по клиенту. */
    val participants: List<JournalParticipant>

    /**
     * Ставит участнику [clientId] отметку [mark]. Публикует событие, только если отметка
     * фактически изменилась. Ошибки: `ATTENDANCE_SESSION_CANCELLED`,
     * `ATTENDANCE_PARTICIPANT_NOT_FOUND`, `ATTENDANCE_CANNOT_UNMARK_COMPLETED`,
     * `ATTENDANCE_LABEL_NOT_APPLICABLE`, `ATTENDANCE_LABEL_ARCHIVED`, `ATTENDANCE_LABEL_NOT_FOUND`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun mark(clientId: ClientId, mark: AttendanceMark)

    /**
     * Отмечает всех неотмеченных участников как отсутствующих с меткой [labelId];
     * уже проставленные отметки не меняются.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun markRemainingAbsent(labelId: AttendanceLabelId)

    /**
     * Добавляет клиента [clientId] разовым участником открытого журнала.
     * Ошибки: `ATTENDANCE_JOURNAL_CLOSED`, `ATTENDANCE_PARTICIPANT_ALREADY_EXISTS`, `CLIENT_NOT_FOUND`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun addOneTime(clientId: ClientId)

    /**
     * Убирает разового участника [clientId] из открытого журнала вместе с отметкой.
     * Ошибки: `ATTENDANCE_JOURNAL_CLOSED`, `ATTENDANCE_PARTICIPANT_NOT_FOUND`,
     * `ATTENDANCE_CANNOT_REMOVE_REGULAR`.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun removeOneTime(clientId: ClientId)
}
