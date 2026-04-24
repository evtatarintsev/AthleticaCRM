package org.athletica.crm.routes

import org.athletica.crm.api.schemas.org.OrgSettingsResponse
import org.athletica.crm.api.schemas.org.UpdateOrgSettingsRequest
import org.athletica.crm.domain.org.Organization
import org.athletica.crm.domain.org.Organizations
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.orgRoutes(organizations: Organizations) {
    route("/org") {
        get("/settings") {
            call.eitherToResponse<OrgSettingsResponse> {
                db.transaction {
                    organizations
                        .current()
                        .toResponse()
                }
            }
        }

        post<UpdateOrgSettingsRequest, OrgSettingsResponse>("/settings/update") { request ->
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

fun Organization.toResponse() = OrgSettingsResponse(name, timezone)
