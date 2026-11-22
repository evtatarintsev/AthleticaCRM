package org.athletica.crm.routes

import arrow.core.raise.context.either
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.athletica.crm.api.schemas.widgets.PublicScheduleRequest
import org.athletica.crm.api.schemas.widgets.PublicScheduleResponse
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.widgets.PublicScheduleQuery
import org.athletica.crm.storage.Database
import kotlin.time.Clock

/**
 * Публичное расписание виджета `GET /api/public/schedule-widget` для страницы виджета на сайте клуба.
 * ВАЖНО: регистрировать внутри `route("/api")`, но ВНЕ блока `authenticate("auth-jwt")` —
 * страницу открывает посетитель без входа в CRM. Ответ кэшируется на минуту.
 */
fun Route.publicScheduleRoutes(db: Database, views: ReadViews) {
    get("/public/schedule-widget") {
        either {
            val request = call.request.queryParameters.decode<PublicScheduleRequest>()
            val query = PublicScheduleQuery(request.key, request.from, request.to, Clock.System.now(), call.langFromRequest())
            db.transaction { views.publicSchedule.schedule(query) }
        }.fold(
            { call.respondError(it) },
            {
                call.response.header(HttpHeaders.CacheControl, "public, max-age=60")
                call.respond(it)
            },
        )
    }.contract(getContract<PublicScheduleRequest, PublicScheduleResponse>())
}
