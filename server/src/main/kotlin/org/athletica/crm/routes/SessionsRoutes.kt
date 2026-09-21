package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.ktor.server.routing.RoutingCall
import kotlinx.datetime.daysUntil
import org.athletica.crm.api.schemas.sessions.CreateSessionRequest
import org.athletica.crm.api.schemas.sessions.RescheduleSessionRequest
import org.athletica.crm.api.schemas.sessions.SessionDetailRequest
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.api.schemas.sessions.SessionListRequest
import org.athletica.crm.api.schemas.sessions.SessionListResponse
import org.athletica.crm.api.schemas.sessions.SetSessionEmployeesRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.sessions.SessionListQuery
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.sessions.createSession
import org.athletica.crm.usecases.sessions.rescheduleSession
import org.athletica.crm.usecases.sessions.sessionDetail
import kotlin.uuid.Uuid

/** Предельная длина периода запроса списка занятий в днях. */
private const val MAX_PERIOD_DAYS = 62

context(db: Database)
fun RouteWithContext.sessionsRoutes(
    groups: Groups,
    sessions: Sessions,
    employees: Employees,
    views: ReadViews,
) {
    route("/sessions") {
        get<SessionListRequest, SessionListResponse>("/list") { request ->
            db.transaction {
                views.sessionList.list(request.toQuery())
            }
        }

        get<SessionDetailRequest, SessionDetailResponse>("/detail") { request ->
            db.transaction {
                sessionDetail(sessions, groups, request.id)
            }
        }

        post<CreateSessionRequest, SessionDetailResponse>("/create") { request ->
            db.transaction {
                createSession(
                    groups = groups,
                    sessions = sessions,
                    employees = employees,
                    id = request.id,
                    groupId = request.groupId,
                    date = request.date,
                    startTime = request.startTime,
                    endTime = request.endTime,
                    hallId = request.hallId,
                    notes = request.notes,
                )
            }
        }

        post<Unit, Unit>("/{id}/cancel") { _, call ->
            val id = call.pathSessionId()
            db.transaction {
                sessions.byId(id).cancel()
            }
        }

        post<RescheduleSessionRequest, SessionDetailResponse>("/{id}/reschedule") { request, call ->
            val id = call.pathSessionId()
            db.transaction {
                rescheduleSession(sessions, groups, id, request.newDate, request.newStartTime, request.newEndTime, request.newHallId)
            }
        }

        post<SetSessionEmployeesRequest, SessionDetailResponse>("/set-employees") { request ->
            db.transaction {
                val session = sessions.byId(request.sessionId)
                session.setEmployees(employees.byIds(request.employeeIds))
                sessionDetail(sessions, groups, request.sessionId)
            }
        }
    }
}

private fun RoutingCall.pathSessionId(): SessionId = Uuid.parse(parameters["id"]!!).toSessionId()

/**
 * Преобразует запрос списка занятий в параметры выборки, проверяя длину периода.
 * Обязательность самих дат держит схема запроса: без них тело не десериализуется.
 */
context(ctx: RequestContext, raise: Raise<DomainError>)
private fun SessionListRequest.toQuery(): SessionListQuery {
    if (to < from) {
        raise(CommonDomainError("INVALID_SESSION_PERIOD", Messages.InvalidSessionPeriod.localize()))
    }
    if (from.daysUntil(to) + 1 > MAX_PERIOD_DAYS) {
        raise(CommonDomainError("SESSION_PERIOD_TOO_LONG", Messages.SessionPeriodTooLong.localize(MAX_PERIOD_DAYS)))
    }
    return SessionListQuery(from = from, to = to, groupId = groupId)
}
