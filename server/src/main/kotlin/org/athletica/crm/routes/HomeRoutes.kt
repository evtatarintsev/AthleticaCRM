package org.athletica.crm.routes

import org.athletica.crm.api.schemas.home.TodaySessionsResponse
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.hall.Halls
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.home.todaySessions

context(db: Database)
fun RouteWithContext.homeRoutes(
    groups: Groups,
    sessions: Sessions,
    halls: Halls,
) {
    route("/home") {
        get<TodaySessionsResponse>("/today-sessions") {
            todaySessions(db, groups, sessions, halls)
        }
    }
}
