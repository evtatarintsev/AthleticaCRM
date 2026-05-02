package org.athletica.crm.usecases.sessions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Transaction

/** Создаёт разовое занятие вне расписания. */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun createSession(
    groups: Groups,
    sessions: Sessions,
    id: SessionId,
    groupId: GroupId,
    date: LocalDate,
    startTime: LocalTime,
    endTime: LocalTime,
    hallId: HallId,
    notes: String?,
): SessionDetailResponse {
    if (endTime <= startTime) {
        raise(CommonDomainError("INVALID_SESSION_TIME", "Время окончания должно быть позже времени начала"))
    }
    val group = groups.byId(groupId)
    val session =
        sessions.new(
            id = id,
            groupId = groupId,
            date = date,
            startTime = startTime,
            endTime = endTime,
            hallId = hallId,
            notes = notes,
            employeeIds = group.employeeIds,
            originDayOfWeek = null,
            originStartTime = null,
            originDate = null,
        ) ?: raise(CommonDomainError("SESSION_ALREADY_EXISTS", "Занятие уже существует"))
    return session.toDetailResponse(groups)
}
