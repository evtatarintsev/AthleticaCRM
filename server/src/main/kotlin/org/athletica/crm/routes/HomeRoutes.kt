package org.athletica.crm.routes

import arrow.fx.coroutines.parZip
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.schemas.home.TodaySessionItem
import org.athletica.crm.api.schemas.home.TodaySessionsResponse
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.hall.Halls
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Database
import kotlin.time.Clock

context(db: Database)
fun RouteWithContext.homeRoutes(
    groups: Groups,
    sessions: Sessions,
    halls: Halls,
) {
    route("/home") {
        get<TodaySessionsResponse>("/today-sessions") {
            val today = Clock.System
                .now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date

            db.transaction {
                val items = parZip(
                    { sessions.listAll(today, today) },
                    { halls.list().associateBy { it.id } },
                    { groups.list().associate { it.id to it.name } }
                ) { sessions, hallMap, groupNames ->
                    sessions
                        .filter { it.status == "scheduled" }
                        .map { session ->
                            val hallName = hallMap[session.hallId]?.name ?: "Не указан"
                            val groupName = groupNames[session.groupId] ?: ""
                            TodaySessionItem(
                                sessionId = session.id,
                                groupName = groupName,
                                startTime = session.startTime,
                                endTime = session.endTime,
                                hallName = hallName,
                            )
                        }
                        .sortedBy { it.startTime }
                }




                TodaySessionsResponse(date = today, sessions = items)
            }
        }
    }
}
