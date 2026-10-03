package org.athletica.crm.routes

import org.athletica.crm.api.schemas.sportcatalog.SportCatalogDisciplineSchema
import org.athletica.crm.api.schemas.sportcatalog.SportCatalogNamesSchema
import org.athletica.crm.api.schemas.sportcatalog.SportCatalogResponse
import org.athletica.crm.api.schemas.sportcatalog.SportCatalogSportSchema
import org.athletica.crm.core.sportcatalog.CatalogDiscipline
import org.athletica.crm.core.sportcatalog.CatalogNames
import org.athletica.crm.core.sportcatalog.CatalogSport
import org.athletica.crm.core.sportcatalog.SportCatalog

/**
 * Маршрут каталога видов спорта: отдаёт каталог целиком, со всеми языками и синонимами.
 * Ответ не зависит от организации и языка запроса, поэтому к БД не обращается.
 */
fun RouteWithContext.sportCatalogRoutes() {
    route("/sport-catalog") {
        get<Unit, SportCatalogResponse>("/list") {
            SportCatalogResponse(SportCatalog.sports.map { it.toSchema() })
        }
    }
}

/** Схема вида спорта для ответа. */
private fun CatalogSport.toSchema() =
    SportCatalogSportSchema(
        key = key,
        names = names.toSchema(),
        disciplines = disciplines.map { it.toSchema() },
    )

/** Схема дисциплины для ответа. */
private fun CatalogDiscipline.toSchema() =
    SportCatalogDisciplineSchema(
        key = key,
        names = names.toSchema(),
        aliases = aliases,
    )

/** Схема названий для ответа. */
private fun CatalogNames.toSchema() = SportCatalogNamesSchema(ru = ru, en = en)
