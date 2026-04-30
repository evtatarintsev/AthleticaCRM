package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.leadSources.CreateLeadSourceRequest
import org.athletica.crm.api.schemas.leadSources.DeleteLeadSourceRequest
import org.athletica.crm.api.schemas.leadSources.LeadSourceDetailResponse
import org.athletica.crm.api.schemas.leadSources.LeadSourceListResponse
import org.athletica.crm.api.schemas.leadSources.UpdateLeadSourceRequest
import org.athletica.crm.domain.leadSource.LeadSource
import org.athletica.crm.domain.leadSource.LeadSources
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.leadSourcesRoutes(leadSources: LeadSources) {
    route("/lead-sources") {
        get<LeadSourceListResponse>("/list") {
            db.transaction {
                leadSources.list()
                    .map { LeadSourceDetailResponse(id = it.id, name = it.name) }
                    .let { LeadSourceListResponse(it) }
            }
        }

        post<CreateLeadSourceRequest, Unit>("/create") { request ->
            db.transaction {
                leadSources.create(LeadSource(request.id, request.name))
            }
        }

        post<UpdateLeadSourceRequest, Unit>("/update") { request ->
            db.transaction {
                leadSources.update(LeadSource(request.id, request.name))
            }
        }

        post<DeleteLeadSourceRequest, Unit>("/delete") { request ->
            db.transaction {
                leadSources.delete(request.ids)
            }
        }
    }
}
