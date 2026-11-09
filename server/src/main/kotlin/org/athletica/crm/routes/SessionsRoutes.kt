package org.athletica.crm.routes

import io.ktor.server.routing.RoutingCall
import org.athletica.crm.api.schemas.sessions.CreateSessionRequest
import org.athletica.crm.api.schemas.sessions.RescheduleSessionRequest
import org.athletica.crm.api.schemas.sessions.SessionDetailRequest
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.api.schemas.sessions.SetSessionEmployeesRequest
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.sessions.createSession
import org.athletica.crm.usecases.sessions.rescheduleSession
import kotlin.uuid.Uuid

context(db: Database)
fun RouteWithContext.sessionsRoutes(
    sessions: Sessions,
    employees: Employees,
    views: ReadViews,
) {
    route("/sessions") {
        get<SessionDetailRequest, SessionDetailResponse>("/detail") { request ->
            db.transaction {
                views.sessionDetail.detail(request.id)
            }
        }

        post<CreateSessionRequest, SessionDetailResponse>("/create") { request ->
            db.transaction {
                createSession(
                    sessions = sessions,
                    employees = employees,
                    id = request.id,
                    groupId = request.groupId,
                    date = request.date,
                    startTime = request.startTime,
                    endTime = request.endTime,
                    hallId = request.hallId,
                    employeeIds = request.employeeIds,
                    notes = request.notes,
                )
                views.sessionDetail.detail(request.id)
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
                rescheduleSession(sessions, id, request.newDate, request.newStartTime, request.newEndTime, request.newHallId)
                views.sessionDetail.detail(id)
            }
        }

        post<SetSessionEmployeesRequest, SessionDetailResponse>("/set-employees") { request ->
            db.transaction {
                val session = sessions.byId(request.sessionId)
                session.setEmployees(employees.byIds(request.employeeIds))
                views.sessionDetail.detail(request.sessionId)
            }
        }
    }
}

private fun RoutingCall.pathSessionId(): SessionId = Uuid.parse(parameters["id"]!!).toSessionId()
