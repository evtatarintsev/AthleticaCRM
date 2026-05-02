package org.athletica.crm.usecases.home

import arrow.core.raise.context.Raise
import kotlinx.datetime.toKotlinLocalDate
import org.athletica.crm.api.schemas.home.TodaySessionItem
import org.athletica.crm.api.schemas.home.TodaySessionsResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.groups.Groups
import org.athletica.crm.domain.hall.Halls
import org.athletica.crm.domain.sessions.Sessions
import org.athletica.crm.storage.Database

/**
 * Возвращает занятия на текущую дату для главной страницы.
 * Только запланированные занятия, отсортированные по времени начала.
 */
context(ctx: RequestContext, raise: Raise<DomainError>)
suspend fun todaySessions(
    db: Database,
    groups: Groups,
    sessions: Sessions,
    halls: Halls,
): TodaySessionsResponse {
    val today = java.time.LocalDate.now().toKotlinLocalDate()

    return db.transaction {
        val sessionList = sessions.listAll(today, today)
        val hallMap = halls.list().associateBy { it.id }
        val groupNames = groups.list().associate { it.id to it.name }

        val items =
            sessionList
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

        TodaySessionsResponse(date = today, sessions = items)
    }
}
