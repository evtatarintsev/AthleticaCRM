package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction

@Serializable
private data class SessionSnapshot(
    val date: LocalDate,
    val hallId: HallId,
    val status: String,
    val isRescheduled: Boolean,
    val notes: String?,
    val employeeIds: List<EmployeeId>,
    val isEmployeeAssignmentOverridden: Boolean,
)

private fun Session.snapshot() =
    Json.encodeToString(
        SessionSnapshot(
            date = date,
            hallId = hallId,
            status = status.name.lowercase(),
            isRescheduled = isRescheduled,
            notes = notes,
            employeeIds = employeeIds,
            isEmployeeAssignmentOverridden = isEmployeeAssignmentOverridden,
        ),
    )

/**
 * Декоратор [ScheduledSession], добавляющий запись в журнал аудита при переходах занятия.
 * В журнал пишется состояние занятия после перехода.
 */
class AuditScheduledSession(private val delegate: ScheduledSession, private val audit: AuditLog) : ScheduledSession by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun cancel(): CancelledSession = delegate.cancel().also { logged(it) }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun reschedule(
        newDate: LocalDate,
        newStartTime: LocalTime,
        newEndTime: LocalTime,
        newHallId: HallId,
    ): ScheduledSession = AuditScheduledSession(delegate.reschedule(newDate, newStartTime, newEndTime, newHallId).also { logged(it) }, audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun complete(): CompletedSession = delegate.complete().also { logged(it) }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun setEmployees(employees: List<Employee>): ScheduledSession = AuditScheduledSession(delegate.setEmployees(employees).also { logged(it) }, audit)

    /** Пишет в журнал аудита снимок занятия [session] после перехода. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    private suspend fun logged(session: Session) {
        audit.logUpdate("session", id, session.snapshot())
    }
}

/** Занятие [this] с аудитом переходов; у проведённого и отменённого переходов нет, они не оборачиваются. */
internal fun Session.audited(audit: AuditLog): Session =
    when (this) {
        is ScheduledSession -> AuditScheduledSession(this, audit)
        is CompletedSession, is CancelledSession -> this
    }
