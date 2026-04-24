package org.athletica.crm.routes

import io.ktor.client.request.request
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.disciplines.CreateDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DeleteDisciplineRequest
import org.athletica.crm.api.schemas.disciplines.DisciplineDetailResponse
import org.athletica.crm.api.schemas.disciplines.DisciplineListResponse
import org.athletica.crm.api.schemas.disciplines.UpdateDisciplineRequest
import org.athletica.crm.domain.discipline.Discipline
import org.athletica.crm.domain.discipline.Disciplines
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.disciplinesRoutes(disciplines: Disciplines) {
    route("/disciplines") {
        get("/list") {
            call.eitherToResponse {
                db.transaction {
                    disciplines.list()
                        .map { DisciplineDetailResponse(id = it.id, name = it.name) }
                        .let { DisciplineListResponse(it) }
                }
            }
        }

        post<CreateDisciplineRequest, Unit>("/create") { request ->
            db.transaction {
                disciplines.create(Discipline(request.id, request.name))
            }
        }

        post<UpdateDisciplineRequest, Unit>("/update") { request ->
            db.transaction {
                disciplines.update(Discipline(request.id, request.name))
            }
        }

        post<DeleteDisciplineRequest, Unit>("/delete") { request ->
            db.transaction {
                disciplines.delete(request.ids)
            }
        }
    }
}
