package org.athletica.crm.routes

import io.ktor.server.routing.RoutingCall
import org.athletica.crm.api.schemas.groups.EditGroupRequest
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.api.schemas.groups.SetGroupDisciplinesRequest
import org.athletica.crm.api.schemas.groups.SetGroupEmployeesRequest
import org.athletica.crm.api.schemas.sessions.UpdateGroupScheduleRequest
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.events.DomainEventBus
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.groups.ScheduleSlot
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.groups.GroupListQuery
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.sessions.updateGroupEmployees
import org.athletica.crm.usecases.sessions.updateGroupSchedule
import kotlin.uuid.Uuid
import org.athletica.crm.api.schemas.groups.ScheduleSlot as ScheduleSlotSchema

/**
 * Регистрирует маршруты для работы с группами.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.groupsRoutes(
    groups: Groups,
    employees: Employees,
    sessions: Sessions,
    views: ReadViews,
    bus: DomainEventBus,
) {
    route("/groups") {
        post<GroupListRequest, GroupListResponse>("/list") { request ->
            db.transaction {
                views.groupList.list(request.toQuery())
            }
        }

        get<GroupDetailRequest, GroupDetailResponse>("/detail") { request ->
            db.transaction {
                views.groupDetail.byId(request.id)
            }
        }

        get<Unit, List<GroupSelectItem>>("/list-for-select") {
            db.transaction {
                views.groupList.forSelect()
            }
        }

        post<GroupCreateRequest, GroupDetailResponse>("/create") { request ->
            db.transaction {
                val group =
                    groups.new(
                        request.id,
                        request.name,
                        request.schedule.map { it.toDomain() },
                        request.disciplineIds,
                        employees.byIds(request.employeeIds),
                    )
                views.groupDetail.byId(group.id)
            }
        }

        post<EditGroupRequest, GroupDetailResponse>("/edit") { request ->
            db.transaction {
                groups
                    .byId(request.id)
                    .withNew(
                        request.name,
                        request.disciplineIds,
                        request.schedule.map { it.toDomain() },
                        employees.byIds(request.employeeIds),
                    )
                    .save()
                views.groupDetail.byId(request.id)
            }
        }

        post<SetGroupDisciplinesRequest, Unit>("/set-disciplines") { request ->
            db.transaction {
                groups
                    .byId(request.groupId)
                    .withNewDisciplines(request.disciplineIds)
                    .save()
            }
        }

        post<SetGroupEmployeesRequest, Unit>("/set-employees") { request ->
            db.transaction {
                updateGroupEmployees(groups, sessions, employees, request.groupId, request.employeeIds)
            }
        }

        post<UpdateGroupScheduleRequest, Unit>("/{groupId}/schedule") { request, call ->
            val groupId = call.pathGroupId()
            db.transaction {
                updateGroupSchedule(groups, sessions, bus, groupId, request.schedule.map { it.toDomain() })
            }
        }
    }
}

private fun RoutingCall.pathGroupId(): GroupId = Uuid.parse(parameters["groupId"]!!).toGroupId()

/** Преобразует запрос списка групп в параметры выборки. */
private fun GroupListRequest.toQuery() =
    GroupListQuery(
        nameQuery = name,
        disciplineIds = disciplineIds,
        employeeIds = employeeIds,
    )

fun ScheduleSlotSchema.toDomain() = ScheduleSlot(dayOfWeek, startAt, endAt, hallId)
