package org.athletica.crm.domain.attendance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Журналы посещаемости занятий организации. */
interface SessionJournals {
    /** Журнал занятия [sessionId]; ошибка `SESSION_NOT_FOUND`, если занятия нет. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(sessionId: SessionId): SessionJournal
}
