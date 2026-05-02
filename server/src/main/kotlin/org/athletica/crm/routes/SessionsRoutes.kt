package org.athletica.crm.routes

import io.ktor.server.routing.RoutingCall
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.sessions.CreateSessionRequest
import org.athletica.crm.api.schemas.sessions.RescheduleSessionRequest
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.api.schemas.sessions.SessionListResponse
import org.athletica.crm.api.schemas.sessions.SetSessionEmployeesRequest
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.sessions.cancelSession
import org.athletica.crm.usecases.sessions.createSession
import org.athletica.crm.usecases.sessions.rescheduleSession
import org.athletica.crm.usecases.sessions.sessionDetail
import org.athletica.crm.usecases.sessions.sessionList
import kotlin.uuid.Uuid

context(db: Database)
fun RouteWithContext.sessionsRoutes(
    groups: Groups,
    sessions: Sessions,
) {
    route("/sessions") {
        get<SessionListResponse>("/list") { call ->
            val from = call.queryDate("from") ?: LocalDate.fromEpochDays(0)
            val to = call.queryDate("to") ?: LocalDate.fromEpochDays(Int.MAX_VALUE / 2)
            val groupId = call.queryGroupId()
            sessionList(db, groups, sessions, groupId, from, to)
        }

        get<SessionDetailResponse>("/{id}") { call ->
            val id = call.pathSessionId()
            db.transaction {
                sessionDetail(sessions, groups, id)
            }
        }

        post<CreateSessionRequest, SessionDetailResponse>("/create") { request ->
            db.transaction {
                createSession(
                    groups = groups,
                    sessions = sessions,
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
                cancelSession(sessions, id)
            }
        }

        post<RescheduleSessionRequest, SessionDetailResponse>("/{id}/reschedule") { request, call ->
            val id = call.pathSessionId()
            db.transaction {
                rescheduleSession(sessions, groups, id, request.newDate, request.newStartTime, request.newEndTime, request.newHallId)
            }
        }

        post<SetSessionEmployeesRequest, SessionDetailResponse>("/{id}/set-employees") { request, call ->
            val id = call.pathSessionId()
            db.transaction {
                val session = sessions.byId(id)
                session.setEmployees(request.employeeIds)
                sessionDetail(sessions, groups, id)
            }
        }
    }
}

private fun RoutingCall.queryDate(name: String): LocalDate? = request.queryParameters[name]?.let { LocalDate.parse(it) }

private fun RoutingCall.queryGroupId(): GroupId? = request.queryParameters["groupId"]?.let { Uuid.parse(it).toGroupId() }

private fun RoutingCall.pathSessionId(): SessionId = Uuid.parse(parameters["id"]!!).toSessionId()
