package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.halls.CreateHallRequest
import org.athletica.crm.api.schemas.halls.DeleteHallRequest
import org.athletica.crm.api.schemas.halls.HallDetailResponse
import org.athletica.crm.api.schemas.halls.HallListResponse
import org.athletica.crm.api.schemas.halls.UpdateHallRequest
import org.athletica.crm.domain.hall.Halls
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.hallsRoutes(halls: Halls) {
    route("/halls") {
        get<Unit, HallListResponse>("/list") {
            db.transaction {
                halls
                    .list()
                    .map { HallDetailResponse(id = it.id, name = it.name) }
                    .let { HallListResponse(it) }
            }
        }

        post<CreateHallRequest, Unit>("/create") { request ->
            db.transaction {
                halls.new(request.id, request.name).save()
            }
        }

        post<UpdateHallRequest, Unit>("/update") { request ->
            db.transaction {
                halls.byId(request.id).withNew(name = request.name).save()
            }
        }

        post<DeleteHallRequest, Unit>("/delete") { request ->
            db.transaction {
                halls.byIds(request.ids).forEach { it.delete() }
            }
        }
    }
}
