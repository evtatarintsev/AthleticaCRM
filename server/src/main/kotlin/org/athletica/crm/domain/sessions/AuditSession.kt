package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

@Serializable
private data class SessionSnapshot(
    val date: LocalDate,
    val hallId: HallId,
    val status: String,
    val isRescheduled: Boolean,
    val notes: String?,
)

private fun Session.snapshot() = Json.encodeToString(SessionSnapshot(date, hallId, status, isRescheduled, notes))

/**
 * Декоратор [Session], добавляющий запись в журнал аудита при изменении состояния занятия.
 */
class AuditSession(private val delegate: Session, private val audit: AuditLog) : Session by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun cancel() =
        delegate.cancel().also {
            audit.logUpdate("session", id, delegate.snapshot())
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun reschedule(
        newDate: LocalDate,
        newStartTime: LocalTime,
        newEndTime: LocalTime,
        newHallId: HallId,
    ) = delegate.reschedule(newDate, newStartTime, newEndTime, newHallId).also {
        audit.logUpdate("session", id, delegate.snapshot())
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun complete() =
        delegate.complete().also {
            audit.logUpdate("session", id, delegate.snapshot())
        }
}
