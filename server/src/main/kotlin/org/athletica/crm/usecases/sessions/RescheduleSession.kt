package org.athletica.crm.usecases.sessions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Transaction

/** Переносит занятие на другую дату или время. Устанавливает флаг [Session.isRescheduled]. */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun rescheduleSession(
    sessions: Sessions,
    id: SessionId,
    newDate: LocalDate,
    newStartTime: LocalTime,
    newEndTime: LocalTime,
    newHallId: HallId,
): SessionDetailResponse {
    if (newEndTime <= newStartTime) {
        raise(CommonDomainError("INVALID_SESSION_TIME", "Время окончания должно быть позже времени начала"))
    }
    val session = sessions.byId(id)
    session.reschedule(newDate, newStartTime, newEndTime, newHallId)
    return sessions.byId(id).toDetailResponse()
}
