package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.org.OrgSettingsResponse
import org.athletica.crm.api.schemas.org.UpdateOrgSettingsRequest
import org.athletica.crm.domain.employees.EmployeePermissions
import org.athletica.crm.domain.org.Organization
import org.athletica.crm.domain.org.Organizations
import org.athletica.crm.storage.Database

context(db: Database, _: EmployeePermissions)
fun Route.orgRoutes(organizations: Organizations) {
    route("/org") {
        getWithContext("/settings") {
            call.eitherToResponse<OrgSettingsResponse> {
                db.transaction {
                    organizations
                        .current()
                        .toResponse()
                }
            }
        }

        postWithContext("/settings/update") {
            call.eitherToResponse<OrgSettingsResponse> {
                val request = call.receive<UpdateOrgSettingsRequest>()
                db.transaction {
                    organizations
                        .current()
                        .withNew(request.name, request.timezone)
                        .apply { save() }
                        .toResponse()
                }
            }
        }
    }
}

fun Organization.toResponse() = OrgSettingsResponse(name, timezone)
