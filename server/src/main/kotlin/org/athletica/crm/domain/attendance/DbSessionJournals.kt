package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.domain.events.DomainEvents
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * Реализация [SessionJournals] поверх PostgreSQL.
 * [labels] проверяет метки отметок, [events] публикует события об отметках.
 */
class DbSessionJournals(
    private val labels: AttendanceLabels,
    private val events: DomainEvents,
) : SessionJournals {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(sessionId: SessionId): SessionJournal {
        val header =
            tr
                .sql("SELECT group_id, date, status::text AS status FROM sessions WHERE id = :id AND org_id = :orgId")
                .bind("id", sessionId)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row ->
                    Triple(
                        row.asUuid("group_id").toGroupId(),
                        row.asLocalDate("date"),
                        SessionStatus.valueOf(row.asString("status").uppercase()),
                    )
                }
                ?: raise(CommonDomainError("SESSION_NOT_FOUND", "Занятие не найдено"))
        val (groupId, date, status) = header
        return DbSessionJournal(
            sessionId = sessionId,
            groupId = groupId,
            date = date,
            status = status,
            participants = SessionRoster.load(sessionId, groupId, date, status),
            labels = labels,
            events = events,
        )
    }
}
