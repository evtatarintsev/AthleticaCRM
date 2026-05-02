package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.halls.CreateHallRequest
import org.athletica.crm.api.schemas.halls.DeleteHallRequest
import org.athletica.crm.api.schemas.halls.HallDetailResponse
import org.athletica.crm.api.schemas.halls.HallListResponse
import org.athletica.crm.api.schemas.halls.UpdateHallRequest
import org.athletica.crm.domain.hall.Hall
import org.athletica.crm.domain.hall.Halls
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.hallsRoutes(halls: Halls) {
    route("/halls") {
        get<HallListResponse>("/list") {
            db.transaction {
                halls
                    .list()
                    .map { HallDetailResponse(id = it.id, name = it.name) }
                    .let { HallListResponse(it) }
            }
        }

        post<CreateHallRequest, Unit>("/create") { request ->
            db.transaction {
                halls.create(Hall(request.id, request.name))
            }
        }

        post<UpdateHallRequest, Unit>("/update") { request ->
            db.transaction {
                halls.update(Hall(request.id, request.name))
            }
        }

        post<DeleteHallRequest, Unit>("/delete") { request ->
            db.transaction {
                halls.delete(request.ids)
            }
        }
    }
}
