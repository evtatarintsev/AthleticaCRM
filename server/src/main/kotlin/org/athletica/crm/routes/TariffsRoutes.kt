package org.athletica.crm.routes

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
                tariffs.new(
                    id = request.id,
                    name = request.name,
                    sessions = request.sessions,
                    durationValue = request.durationValue,
                    durationUnit = request.durationUnit,
                    price = request.price,
                ).save()
            }
        }

        post<UpdateTariffPlanRequest, Unit>("/update") { request ->
            db.transaction {
                tariffs.byId(request.id)
                    .withNew(
                        name = request.name,
                        sessions = request.sessions,
                        durationValue = request.durationValue,
                        durationUnit = request.durationUnit,
                        price = request.price,
                    ).save()
            }
        }

        post<ArchiveTariffPlanRequest, Unit>("/archive") { request ->
            db.transaction {
                val tariff = tariffs.byId(request.id)
                if (request.archived) {
                    tariff.archive()
                } else {
                    tariff.unarchive()
                }
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
