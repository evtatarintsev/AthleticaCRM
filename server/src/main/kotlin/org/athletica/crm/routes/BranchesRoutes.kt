package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.branches.BranchCreateRequest
import org.athletica.crm.api.schemas.branches.BranchDetailResponse
import org.athletica.crm.api.schemas.branches.BranchListResponse
import org.athletica.crm.api.schemas.branches.BranchUpdateRequest
import org.athletica.crm.api.schemas.branches.DeleteBranchRequest
import org.athletica.crm.domain.branch.Branches
import org.athletica.crm.storage.Database

context(db: Database)
fun RouteWithContext.branchesRoutes(branches: Branches) {
    route("/branches") {
        get<Unit, BranchListResponse>("/list") {
            db.transaction {
                branches
                    .list()
                    .map { BranchDetailResponse(id = it.id, name = it.name) }
                    .let { BranchListResponse(it) }
            }
        }

        post<BranchCreateRequest, Unit>("/create") { request ->
            db.transaction {
                branches.new(request.id, request.name).save()
            }
        }

        post<BranchUpdateRequest, Unit>("/update") { request ->
            db.transaction {
                branches.byId(request.id).withNew(name = request.name).save()
            }
        }

        post<DeleteBranchRequest, Unit>("/delete") { request ->
            db.transaction {
                branches.byIds(request.ids).forEach { it.delete() }
            }
        }
    }
}
