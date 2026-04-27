package org.athletica.crm.routes

import io.ktor.server.routing.RoutingCall
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupDiscipline
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.api.schemas.groups.SetGroupDisciplinesRequest
import org.athletica.crm.api.schemas.sessions.UpdateGroupScheduleRequest
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.domain.discipline.Discipline
import org.athletica.crm.domain.discipline.Disciplines
import org.athletica.crm.domain.events.DomainEventBus
import org.athletica.crm.domain.events.GroupCreated
import org.athletica.crm.domain.groups.Group
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.groups.ScheduleSlot
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.sessions.updateGroupSchedule
import kotlin.uuid.Uuid
import org.athletica.crm.api.schemas.groups.ScheduleSlot as ScheduleSlotSchema

context(db: Database)
fun RouteWithContext.groupsRoutes(
    groups: Groups,
    disciplines: Disciplines,
    sessions: Sessions,
    bus: DomainEventBus,
) {
    route("/groups") {
        get<GroupListResponse>("/list") {
            db.transaction {
                groups.list().toListResponse()
            }
        }

        get<List<GroupSelectItem>>("/list-for-select") {
            db.transaction {
                groups.list().toGroupSelectItems()
            }
        }

        post<GroupCreateRequest, GroupDetailResponse>("/create") { request ->
            db.transaction {
                val group =
                    groups
                        .new(
                            request.id,
                            request.name,
                            request.schedule.map { it.toDomain() },
                            request.disciplineIds,
                        )
                bus.publish(GroupCreated(group.id))
                group.toGroupDetailResponse(disciplines.list())
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

        post<UpdateGroupScheduleRequest, Unit>("/{groupId}/schedule") { request, call ->
            val groupId = call.pathGroupId()
            db.transaction {
                updateGroupSchedule(groups, sessions, bus, groupId, request.schedule.map { it.toDomain() })
            }
        }
    }
}

private fun RoutingCall.pathGroupId(): GroupId = Uuid.parse(parameters["groupId"]!!).toGroupId()

fun List<Group>.toListResponse() = GroupListResponse(map { GroupListItem(it.id, it.name) })

fun List<Group>.toGroupSelectItems() = map { GroupSelectItem(it.id, it.name) }

fun Group.toGroupDetailResponse(allDisciplines: List<Discipline>) = GroupDetailResponse(id, name, schedule.map { it.toSchema() }, allDisciplines.mapToGroupDisciplines(disciplines))

fun ScheduleSlotSchema.toDomain() = ScheduleSlot(dayOfWeek, startAt, endAt)

fun ScheduleSlot.toSchema() = ScheduleSlotSchema(dayOfWeek, startAt, endAt)

fun List<Discipline>.mapToGroupDisciplines(ids: List<DisciplineId>) = filter { ids.contains(it.id) }.map { GroupDiscipline(it.id, it.name) }
