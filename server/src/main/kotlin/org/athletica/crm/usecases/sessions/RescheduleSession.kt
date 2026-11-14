package org.athletica.crm.usecases.sessions

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Transaction

/** Переносит занятие на другую дату или время. Устанавливает флаг [Session.isRescheduled]. */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
suspend fun rescheduleSession(
    sessions: Sessions,
    id: SessionId,
    newDate: LocalDate,
    newStartTime: LocalTime,
    newEndTime: LocalTime,
    newHallId: HallId,
) {
    val session = sessions.byId(id)
    session.reschedule(newDate, newStartTime, newEndTime, newHallId)
}
