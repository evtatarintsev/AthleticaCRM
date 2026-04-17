package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.org.UpdateOrgSettingsRequest
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.org.getOrgSettings
import org.athletica.crm.usecases.org.updateOrgSettings

context(db: Database)
fun Route.orgRoutes() {
    route("/org") {
        getWithContext("/settings") {
            call.eitherToResponse {
                getOrgSettings().bind()
            }
        }

        postWithContext("/settings/update") {
            call.eitherToResponse {
                val request = call.receive<UpdateOrgSettingsRequest>()
                updateOrgSettings(request).bind()
            }
        }
    }
}
