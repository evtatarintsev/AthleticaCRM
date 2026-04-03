package org.athletica.crm.routes

import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.disciplines.CreateDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DeleteDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DisciplineListResponse
import org.athletica.crm.api.schemas.disciplines.UpdateDisciplineRequest
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.db.Database
import org.athletica.crm.usecases.disciplines.createDiscipline
import org.athletica.crm.usecases.disciplines.deleteDiscipline
import org.athletica.crm.usecases.disciplines.disciplineList
import org.athletica.crm.usecases.disciplines.updateDiscipline

context(db: Database, audit: AuditLog)
fun Route.disciplinesRoutes() {
    route("/disciplines") {
        getWithContext("/list") {
            call.eitherToResponse {
                val disciplines = disciplineList().bind()
                DisciplineListResponse(disciplines)
            }
        }

        postWithContext("/create") {
            call.eitherToResponse {
                val request = call.receive<CreateDisciplineRequest>()
                val result = createDiscipline(request).bind()
                result
            }
        }

        postWithContext("/update") {
            call.eitherToResponse {
                val request = call.receive<UpdateDisciplineRequest>()
                val result = updateDiscipline(request).bind()
                result
            }
        }

        postWithContext("/delete") {
            call.eitherToResponse {
                val request = call.receive<DeleteDisciplineRequest>()
                deleteDiscipline(request).bind()
            }
        }
    }
}
