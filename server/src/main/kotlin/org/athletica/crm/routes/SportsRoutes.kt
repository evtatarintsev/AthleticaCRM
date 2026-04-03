package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.sports.CreateSportRequest
import org.athletica.crm.api.schemas.sports.DeleteSportRequest
import org.athletica.crm.api.schemas.sports.SportListResponse
import org.athletica.crm.api.schemas.sports.UpdateSportRequest
import org.athletica.crm.audit.AuditActionType
import org.athletica.crm.audit.AuditService
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.sports.createSport
import org.athletica.crm.usecases.sports.deleteSport
import org.athletica.crm.usecases.sports.sportList
import org.athletica.crm.usecases.sports.updateSport

context(db: Database, audit: AuditService)
fun Route.sportsRoutes() {
    route("/sports") {
        getWithContext("/list") {
            call.eitherToResponse {
                val sports = sportList().bind()
                SportListResponse(sports)
            }
        }

        postWithContext("/create") {
            val ip = call.clientIp()
            call.eitherToResponse {
                val request = call.receive<CreateSportRequest>()
                val result = createSport(request).bind()
                auditLog(AuditActionType.CREATE, "sport", result.id, ip)
                result
            }
        }

        postWithContext("/update") {
            val ip = call.clientIp()
            call.eitherToResponse {
                val request = call.receive<UpdateSportRequest>()
                val result = updateSport(request).bind()
                auditLog(AuditActionType.UPDATE, "sport", result.id, ip)
                result
            }
        }

        postWithContext("/delete") {
            val ip = call.clientIp()
            call.eitherToResponse {
                val request = call.receive<DeleteSportRequest>()
                deleteSport(request).bind()
                auditLog(AuditActionType.DELETE, "sport", ipAddress = ip)
                Unit
            }
        }
    }
}
