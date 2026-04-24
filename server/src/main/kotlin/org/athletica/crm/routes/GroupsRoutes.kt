package org.athletica.crm.routes

import arrow.core.raise.context.bind
import io.ktor.client.request.request
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.SetGroupDisciplinesRequest
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.groups.createGroup
import org.athletica.crm.usecases.groups.groupList
import org.athletica.crm.usecases.groups.groupListForSelect
import org.athletica.crm.usecases.groups.setGroupDisciplines

context(db: Database, audit: AuditLog)
fun RouteWithContext.groupsRoutes() {
    route("/groups") {
        get("/list") {
            call.eitherToResponse {
                val groups = groupList(GroupListRequest()).bind()
                GroupListResponse(groups)
            }
        }

        get("/list-for-select") {
            call.eitherToResponse {
                groupListForSelect().bind()
            }
        }

        post<GroupCreateRequest, GroupDetailResponse>("/create") { request ->
            createGroup(request).bind()
        }

        post<SetGroupDisciplinesRequest, Unit>("/set-disciplines") { request ->
            setGroupDisciplines(request).bind()
        }
    }
}
