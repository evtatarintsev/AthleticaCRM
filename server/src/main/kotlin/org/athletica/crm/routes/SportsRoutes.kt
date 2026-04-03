package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.sports.CreateSportRequest
import org.athletica.crm.api.schemas.sports.DeleteSportRequest
import org.athletica.crm.api.schemas.sports.SportListResponse
import org.athletica.crm.api.schemas.sports.UpdateSportRequest
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.sports.createSport
import org.athletica.crm.usecases.sports.deleteSport
import org.athletica.crm.usecases.sports.sportList
import org.athletica.crm.usecases.sports.updateSport

context(db: Database, audit: AuditLog)
fun Route.sportsRoutes() {
    route("/sports") {
        getWithContext("/list") {
            call.eitherToResponse {
                val sports = sportList().bind()
                SportListResponse(sports)
            }
        }

        postWithContext("/create") {
            call.eitherToResponse {
                val request = call.receive<CreateSportRequest>()
                val result = createSport(request).bind()
                result
            }
        }

        postWithContext("/update") {
            call.eitherToResponse {
                val request = call.receive<UpdateSportRequest>()
                val result = updateSport(request).bind()
                result
            }
        }

        postWithContext("/delete") {
            call.eitherToResponse {
                val request = call.receive<DeleteSportRequest>()
                deleteSport(request).bind()
            }
        }
    }
}
