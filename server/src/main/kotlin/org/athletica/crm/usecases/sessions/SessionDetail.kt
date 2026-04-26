package org.athletica.crm.usecases.sessions

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.sessions.Session
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Transaction

/** Возвращает детали одного занятия. */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun sessionDetail(
    sessions: Sessions,
    id: SessionId,
): SessionDetailResponse = sessions.byId(id).toDetailResponse()

fun Session.toDetailResponse() =
    SessionDetailResponse(
        id = id,
        groupId = groupId,
        groupName = groupName,
        date = date,
        startTime = startTime,
        endTime = endTime,
        status = status,
        isManual = isManual,
        isRescheduled = isRescheduled,
        notes = notes,
    )
