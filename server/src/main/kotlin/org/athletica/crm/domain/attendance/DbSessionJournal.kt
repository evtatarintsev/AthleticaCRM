package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.attendance.AttendancePresence
import org.athletica.crm.core.attendance.ParticipationKind
import org.athletica.crm.core.entityids.AttendanceLabelId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.domain.events.AttendanceChanged
import org.athletica.crm.domain.events.AttendanceMarkSnapshot
import org.athletica.crm.domain.events.AttendanceMarked
import org.athletica.crm.domain.events.DomainEvents
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asUuid

/** Реализация [SessionJournal] поверх PostgreSQL; см. [SessionRoster] о составе занятия. */
class DbSessionJournal(
    override val sessionId: SessionId,
    override val groupId: GroupId,
    override val date: LocalDate,
    override val status: SessionStatus,
    override val participants: List<JournalParticipant>,
    private val labels: AttendanceLabels,
    private val events: DomainEvents,
) : SessionJournal {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun mark(clientId: ClientId, mark: AttendanceMark) {
        ensureNotCancelled()
        val participant = participant(clientId)
        if (status == SessionStatus.COMPLETED && mark is AttendanceMark.Unmarked) {
            raise(CommonDomainError("ATTENDANCE_CANNOT_UNMARK_COMPLETED", Messages.AttendanceCannotUnmarkCompleted.localize()))
        }
        if (participant.mark == mark) {
            return
        }
        ensureLabelsAssignable(mark, participant.mark)
        write(participant, mark)
        val event =
            if (participant.mark is AttendanceMark.Unmarked) {
                AttendanceMarked(sessionId, clientId, mark.toSnapshot(), ctx.employeeId)
            } else {
                AttendanceChanged(sessionId, clientId, participant.mark.toSnapshot(), mark.toSnapshot(), ctx.employeeId)
            }
        events.publish(event)
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun markRemainingAbsent(labelId: AttendanceLabelId) {
        ensureNotCancelled()
        val absent =
            AttendanceMark.Absent
                .from(setOf(labelId))
                .fold({ raise(it) }, { it })
        ensureLabelsAssignable(absent, AttendanceMark.Unmarked)
        participants
            .filter { it.mark is AttendanceMark.Unmarked }
            .forEach { mark(it.clientId, absent) }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun addOneTime(clientId: ClientId) {
        ensureOpen()
        if (participants.any { it.clientId == clientId }) {
            raise(CommonDomainError("ATTENDANCE_PARTICIPANT_ALREADY_EXISTS", Messages.AttendanceParticipantAlreadyExists.localize()))
        }
        tr
            .sql("SELECT id FROM clients WHERE id = :clientId AND org_id = :orgId")
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { it.asUuid("id") }
            ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))
        tr
            .sql(
                """
                INSERT INTO session_attendance (session_id, client_id, kind)
                VALUES (:sessionId, :clientId, :kind::attendance_kind)
                """.trimIndent(),
            )
            .bind("sessionId", sessionId)
            .bind("clientId", clientId)
            .bind("kind", ParticipationKind.ONE_TIME.toDb())
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun removeOneTime(clientId: ClientId) {
        ensureOpen()
        if (participant(clientId).kind != ParticipationKind.ONE_TIME) {
            raise(CommonDomainError("ATTENDANCE_CANNOT_REMOVE_REGULAR", Messages.AttendanceCannotRemoveRegular.localize()))
        }
        tr
            .sql("DELETE FROM session_attendance WHERE session_id = :sessionId AND client_id = :clientId")
            .bind("sessionId", sessionId)
            .bind("clientId", clientId)
            .execute()
    }

    /** Участник [clientId] из состава; ошибка `ATTENDANCE_PARTICIPANT_NOT_FOUND`, если его нет. */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    private fun participant(clientId: ClientId): JournalParticipant =
        participants.find { it.clientId == clientId }
            ?: raise(CommonDomainError("ATTENDANCE_PARTICIPANT_NOT_FOUND", Messages.AttendanceParticipantNotFound.localize()))

    /** Отказ для отменённого занятия: его журнал аннулирован. */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    private fun ensureNotCancelled() {
        if (status == SessionStatus.CANCELLED) {
            raise(CommonDomainError("ATTENDANCE_SESSION_CANCELLED", Messages.AttendanceSessionCancelled.localize()))
        }
    }

    /** Отказ для занятия, журнал которого закрыт или аннулирован: состав меняется только у запланированного. */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    private fun ensureOpen() {
        ensureNotCancelled()
        if (status != SessionStatus.SCHEDULED) {
            raise(CommonDomainError("ATTENDANCE_JOURNAL_CLOSED", Messages.AttendanceJournalClosed.localize()))
        }
    }

    /**
     * Проверяет, что метки отметки [mark] применимы к её состоянию и не архивны.
     * Архивная метка допустима, только если уже стоит на прежней отметке [previous].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun ensureLabelsAssignable(mark: AttendanceMark, previous: AttendanceMark) {
        val presence = mark.presence ?: return
        labels.byIds(mark.labelIds).forEach { label ->
            if (!label.appliesTo(presence)) {
                raise(CommonDomainError("ATTENDANCE_LABEL_NOT_APPLICABLE", Messages.AttendanceLabelNotApplicable.localize()))
            }
            if (label.isArchived && label.id !in previous.labelIds) {
                raise(CommonDomainError("ATTENDANCE_LABEL_ARCHIVED", Messages.AttendanceLabelArchived.localize()))
            }
        }
    }

    /** Сохраняет отметку [mark] участника [participant], создавая строку журнала при первой отметке. */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    private suspend fun write(participant: JournalParticipant, mark: AttendanceMark) {
        val attendanceId =
            tr
                .sql(
                    """
                    INSERT INTO session_attendance (session_id, client_id, kind, presence, marked_by, marked_at)
                    VALUES (:sessionId, :clientId, :kind::attendance_kind, :presence::attendance_presence, :employeeId, now())
                    ON CONFLICT (session_id, client_id) DO UPDATE
                        SET presence = EXCLUDED.presence,
                            marked_by = EXCLUDED.marked_by,
                            marked_at = EXCLUDED.marked_at
                    RETURNING id
                    """.trimIndent(),
                )
                .bind("sessionId", sessionId)
                .bind("clientId", participant.clientId)
                .bind("kind", participant.kind.toDb())
                .bind("presence", mark.presence?.toDb())
                .bind("employeeId", ctx.employeeId)
                .firstOrNull { it.asUuid("id") }
                ?: error("INSERT ... RETURNING не вернул строку журнала")
        tr
            .sql("DELETE FROM session_attendance_labels WHERE attendance_id = :attendanceId")
            .bind("attendanceId", attendanceId)
            .execute()
        mark.labelIds.forEach { labelId ->
            tr
                .sql("INSERT INTO session_attendance_labels (attendance_id, label_id) VALUES (:attendanceId, :labelId)")
                .bind("attendanceId", attendanceId)
                .bind("labelId", labelId)
                .execute()
        }
    }
}

/** Отметка в виде, публикуемом в доменных событиях. */
fun AttendanceMark.toSnapshot(): AttendanceMarkSnapshot = AttendanceMarkSnapshot(presence, labelIds.sortedBy { it.value.toString() })

/** Значение enum-типа `attendance_presence` в БД. */
internal fun AttendancePresence.toDb(): String = name.lowercase()
