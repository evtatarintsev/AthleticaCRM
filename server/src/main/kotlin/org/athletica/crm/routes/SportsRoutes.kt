package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.sports.CreateSportRequest
import org.athletica.crm.api.schemas.sports.DeleteSportRequest
import org.athletica.crm.api.schemas.sports.SportListResponse
import org.athletica.crm.api.schemas.sports.UpdateSportRequest
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.sports.createSport
import org.athletica.crm.usecases.sports.deleteSport
import org.athletica.crm.usecases.sports.sportList
import org.athletica.crm.usecases.sports.updateSport

context(db: Database)
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
                createSport(request).bind()
            }
        }

        postWithContext("/update") {
            call.eitherToResponse {
                val request = call.receive<UpdateSportRequest>()
                updateSport(request).bind()
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
