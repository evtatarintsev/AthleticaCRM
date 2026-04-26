package org.athletica.crm.usecases.sessions

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Transaction

/** Отменяет занятие. Доступно только для занятий в статусе `scheduled`. */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun cancelSession(
    sessions: Sessions,
    id: SessionId,
) {
    sessions.byId(id).cancel()
}
