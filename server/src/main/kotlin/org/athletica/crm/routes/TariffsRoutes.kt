package org.athletica.crm.routes

import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.tariffs.ArchiveTariffPlanRequest
import org.athletica.crm.api.schemas.tariffs.CreateTariffPlanRequest
import org.athletica.crm.api.schemas.tariffs.TariffPlanListRequest
import org.athletica.crm.api.schemas.tariffs.TariffPlanListResponse
import org.athletica.crm.api.schemas.tariffs.TariffPlanSchema
import org.athletica.crm.api.schemas.tariffs.UpdateTariffPlanRequest
import org.athletica.crm.domain.tariffs.TariffPlan
import org.athletica.crm.domain.tariffs.TariffPlans
import org.athletica.crm.storage.Database

/**
 * Маршруты каталога тарифных планов абонементов (требуют JWT-авторизации).
 */
context(db: Database)
fun RouteWithContext.tariffsRoutes(tariffs: TariffPlans) {
    route("/tariffs") {
        post<TariffPlanListRequest, TariffPlanListResponse>("/list") { request ->
            db.transaction {
                tariffs.list(request.includeArchived)
                    .map { it.toSchema() }
                    .let { TariffPlanListResponse(it) }
            }
        }

        post<CreateTariffPlanRequest, Unit>("/create") { request ->
            db.transaction {
                tariffs.create(
                    TariffPlan(
                        id = request.id,
                        name = request.name,
                        sessions = request.sessions,
                        durationValue = request.durationValue,
                        durationUnit = request.durationUnit,
                        price = request.price,
                        archived = false,
                    ),
                )
            }
        }

        post<UpdateTariffPlanRequest, Unit>("/update") { request ->
            db.transaction {
                tariffs.update(
                    TariffPlan(
                        id = request.id,
                        name = request.name,
                        sessions = request.sessions,
                        durationValue = request.durationValue,
                        durationUnit = request.durationUnit,
                        price = request.price,
                        archived = false,
                    ),
                )
            }
        }

        post<ArchiveTariffPlanRequest, Unit>("/archive") { request ->
            db.transaction {
                tariffs.setArchived(request.id, request.archived)
            }
        }
    }
}

/** Преобразует доменный тариф в схему ответа. */
private fun TariffPlan.toSchema() =
    TariffPlanSchema(
        id = id,
        name = name,
        sessions = sessions,
        durationValue = durationValue,
        durationUnit = durationUnit,
        price = price,
        archived = archived,
    )
