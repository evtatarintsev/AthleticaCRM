package org.athletica.crm.read.sessions

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

fun interface SessionDetailView {
    /** Карточка занятия в текущей организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun detail(id: SessionId): SessionDetailResponse
}
