package org.athletica.crm.routes

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.schemas.home.TodaySessionsResponse
import org.athletica.crm.api.schemas.home.UnclosedSessionsRequest
import org.athletica.crm.api.schemas.home.UnclosedSessionsResponse
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.home.UnclosedSessionsQuery
import org.athletica.crm.storage.Database
import kotlin.time.Clock

/**
 * Регистрирует маршруты главной страницы.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.homeRoutes(views: ReadViews) {
    route("/home") {
        get<Unit, TodaySessionsResponse>("/today-sessions") {
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date

            db.transaction {
                views.todaySchedule.sessionsOn(today)
            }
        }

        get<UnclosedSessionsRequest, UnclosedSessionsResponse>("/unclosed-sessions") { request ->
            val query = UnclosedSessionsQuery(now = Clock.System.now(), limit = request.limit.coerceIn(1, 50))
            db.transaction {
                views.unclosedSessions.unclosed(query)
            }
        }
    }
}
