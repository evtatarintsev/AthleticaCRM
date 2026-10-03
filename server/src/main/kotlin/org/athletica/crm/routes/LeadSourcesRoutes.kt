package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.leadSources.CreateLeadSourceRequest
import org.athletica.crm.api.schemas.leadSources.DeleteLeadSourceRequest
import org.athletica.crm.api.schemas.leadSources.LeadSourceDetailResponse
import org.athletica.crm.api.schemas.leadSources.LeadSourceListResponse
import org.athletica.crm.api.schemas.leadSources.UpdateLeadSourceRequest
import org.athletica.crm.domain.leadSource.LeadSourceDescription
import org.athletica.crm.domain.leadSource.LeadSources
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.leadSourcesRoutes(leadSources: LeadSources) {
    route("/lead-sources") {
        get<Unit, LeadSourceListResponse>("/list") {
            db.transaction {
                leadSources.list()
                    .map { LeadSourceDetailResponse(id = it.id, name = it.name, description = it.description.value) }
                    .let { LeadSourceListResponse(it) }
            }
        }

        post<CreateLeadSourceRequest, Unit>("/create") { request ->
            val description = LeadSourceDescription.from(request.description).bind()
            db.transaction {
                leadSources.new(request.id, request.name, description).save()
            }
        }

        post<UpdateLeadSourceRequest, Unit>("/update") { request ->
            val description = LeadSourceDescription.from(request.description).bind()
            db.transaction {
                leadSources.byId(request.id).withNew(name = request.name, description = description).save()
            }
        }

        post<DeleteLeadSourceRequest, Unit>("/delete") { request ->
            db.transaction {
                leadSources.byIds(request.ids).forEach { it.delete() }
            }
        }
    }
}
