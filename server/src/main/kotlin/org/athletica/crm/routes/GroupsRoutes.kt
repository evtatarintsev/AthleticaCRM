package org.athletica.crm.routes

import io.ktor.server.routing.RoutingCall
import org.athletica.crm.api.schemas.groups.EditGroupRequest
import org.athletica.crm.api.schemas.groups.GroupClient
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupDiscipline
import org.athletica.crm.api.schemas.groups.GroupEmployee
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.api.schemas.groups.SetGroupDisciplinesRequest
import org.athletica.crm.api.schemas.groups.SetGroupEmployeesRequest
import org.athletica.crm.api.schemas.sessions.UpdateGroupScheduleRequest
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.domain.discipline.Discipline
import org.athletica.crm.domain.discipline.Disciplines
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.enrollments.Enrollments
import org.athletica.crm.domain.events.DomainEventBus
import org.athletica.crm.domain.groups.Group
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.groups.ScheduleSlot
import org.athletica.crm.domain.hall.Halls
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.sessions.updateGroupEmployees
import org.athletica.crm.usecases.sessions.updateGroupSchedule
import kotlin.uuid.Uuid
import org.athletica.crm.api.schemas.groups.ScheduleSlot as ScheduleSlotSchema

context(db: Database)
fun RouteWithContext.groupsRoutes(
    groups: Groups,
    disciplines: Disciplines,
    employees: Employees,
    sessions: Sessions,
    halls: Halls,
    enrollments: Enrollments,
    bus: DomainEventBus,
) {
    route("/groups") {
        post<GroupListRequest, GroupListResponse>("/list") { request ->
            db.transaction {
                val allEmployees = employees.list()
                val filtered =
                    groups.list(
                        nameQuery = request.name,
                        disciplineIds = request.disciplineIds,
                        employeeIds = request.employeeIds,
                    )
                filtered.toListResponse(allEmployees, total = groups.totalCount())
            }
        }

        get<GroupDetailRequest, GroupDetailResponse>("/detail") { request ->
            db.transaction {
                val group = groups.byId(request.id)
                val hallNames = halls.list().associate { it.id to it.name }
                val activeClients = enrollments.activeClients(group.id)
                group.toGroupDetailResponse(disciplines.list(), employees.list(), hallNames, activeClients)
            }
        }

        get<Unit, List<GroupSelectItem>>("/list-for-select") {
            db.transaction {
                groups.list().toGroupSelectItems()
            }
        }

        post<GroupCreateRequest, GroupDetailResponse>("/create") { request ->
            db.transaction {
                groups
                    .new(
                        request.id,
                        request.name,
                        request.schedule.map { it.toDomain() },
                        request.disciplineIds,
                        employees.byIds(request.employeeIds),
                    )
                    .toGroupDetailResponse(disciplines.list(), employees.list(), emptyMap(), emptyList())
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
                    .apply { save() }
                    .toGroupDetailResponse(disciplines.list(), employees.list(), emptyMap(), emptyList())
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

fun List<Group>.toListResponse(
    allEmployees: List<Employee> = emptyList(),
    total: Int = size,
) = GroupListResponse(
    groups =
        map { group ->
            GroupListItem(
                id = group.id,
                name = group.name,
                schedule = group.schedule.map { it.toSchema() },
                employees = allEmployees.mapToGroupEmployees(group.employeeIds),
            )
        },
    total = total.toUInt(),
)

fun List<Group>.toGroupSelectItems() = map { GroupSelectItem(it.id, it.name) }

fun Group.toGroupDetailResponse(
    allDisciplines: List<Discipline>,
    allEmployees: List<Employee>,
    hallNames: Map<HallId, String>,
    activeClients: List<Pair<ClientId, String>>,
) = GroupDetailResponse(
    id = id,
    name = name,
    schedule = schedule.map { it.toSchema(hallNames) },
    disciplines = allDisciplines.mapToGroupDisciplines(disciplines),
    employees = allEmployees.mapToGroupEmployees(employeeIds),
    clients = activeClients.map { (id, name) -> GroupClient(id, name) },
)

fun ScheduleSlotSchema.toDomain() = ScheduleSlot(dayOfWeek, startAt, endAt, hallId)

fun ScheduleSlot.toSchema(hallNames: Map<HallId, String> = emptyMap()) = ScheduleSlotSchema(dayOfWeek, startAt, endAt, hallId, hallNames[hallId])

fun List<Discipline>.mapToGroupDisciplines(ids: List<DisciplineId>) = filter { ids.contains(it.id) }.map { GroupDiscipline(it.id, it.name) }

fun List<Employee>.mapToGroupEmployees(ids: List<EmployeeId>) = filter { ids.contains(it.id) }.map { GroupEmployee(it.id, it.name, it.avatarId) }
