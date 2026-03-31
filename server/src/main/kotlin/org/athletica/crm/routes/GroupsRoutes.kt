package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupListRequest
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.groups.createGroup
import org.athletica.crm.usecases.groups.groupList

context(db: Database)
fun Route.groupsRoutes() {
    route("/groups") {
        getWithContext("/list") {
            call.eitherToResponse {
                val groups = groupList(GroupListRequest()).bind()
                GroupListResponse(groups)
            }
        }

        postWithContext("/create") {
            call.eitherToResponse {
                val request = call.receive<GroupCreateRequest>()
                createGroup(request).bind()
            }
        }
    }
}
