package org.athletica.crm.routes

import org.athletica.crm.api.schemas.groups.EditGroupRequest
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.api.schemas.groups.SetGroupDisciplinesRequest
import org.athletica.crm.api.schemas.groups.SetGroupEmployeesRequest
import org.athletica.crm.api.schemas.groups.SetGroupScheduleRequest
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.groups.GroupSchedule
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.groups.NewSlot
import org.athletica.crm.domain.sessions.ScheduleSync
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.groups.GroupListQuery
import org.athletica.crm.storage.Database
import org.athletica.crm.api.schemas.groups.ScheduleSlot as ScheduleSlotSchema

/**
 * Регистрирует маршруты для работы с группами.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.groupsRoutes(
    groups: Groups,
    employees: Employees,
    schedule: GroupSchedule,
    sync: ScheduleSync,
    views: ReadViews,
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
                        request.disciplineIds,
                        employees.byIds(request.employeeIds),
                    )
                sync.sync()
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
                        employees.byIds(request.employeeIds),
                    )
                    .save()
                sync.sync()
                views.groupDetail.byId(request.id)
            }
        }

        post<SetGroupScheduleRequest, GroupDetailResponse>("/set-schedule") { request ->
            db.transaction {
                schedule.setFrom(request.groupId, request.effectiveFrom, request.slots.map { it.toNewSlot() })
                sync.sync()
                views.groupDetail.byId(request.groupId)
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
                groups
                    .byId(request.groupId)
                    .withNewEmployees(employees.byIds(request.employeeIds))
                    .save()
                sync.sync()
            }
        }
    }
}

/** Преобразует запрос списка групп в параметры выборки. */
private fun GroupListRequest.toQuery() =
    GroupListQuery(
        nameQuery = name,
        disciplineIds = disciplineIds,
        employeeIds = employeeIds,
    )

/** Преобразует слот из запроса в доменное правило расписания; период действия задаёт домен. */
fun ScheduleSlotSchema.toNewSlot() = NewSlot(dayOfWeek, startAt, endAt, hallId)
