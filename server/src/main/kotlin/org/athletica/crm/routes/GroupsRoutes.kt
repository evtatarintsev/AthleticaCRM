package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.SetGroupDisciplinesRequest
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.groups.createGroup
import org.athletica.crm.usecases.groups.groupList
import org.athletica.crm.usecases.groups.groupListForSelect
import org.athletica.crm.usecases.groups.setGroupDisciplines

context(db: Database, audit: AuditLog)
fun Route.groupsRoutes() {
    route("/groups") {
        getWithContext("/list") {
            call.eitherToResponse {
                val groups = groupList(GroupListRequest()).bind()
                GroupListResponse(groups)
            }
        }

        getWithContext("/list-for-select") {
            call.eitherToResponse {
                groupListForSelect().bind()
            }
        }

        postWithContext("/create") {
            call.eitherToResponse {
                val request = call.receive<GroupCreateRequest>()
                val result = createGroup(request).bind()
                result
            }
        }

        postWithContext("/set-disciplines") {
            call.eitherToResponse {
                val request = call.receive<SetGroupDisciplinesRequest>()
                setGroupDisciplines(request).bind()
            }
        }
    }
}
