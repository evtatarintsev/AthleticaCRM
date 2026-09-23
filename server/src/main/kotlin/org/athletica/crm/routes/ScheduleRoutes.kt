package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.daysUntil
import org.athletica.crm.api.schemas.schedule.ScheduleListRequest
import org.athletica.crm.api.schemas.schedule.ScheduleListResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.schedule.ScheduleQuery
import org.athletica.crm.storage.Database

/** Предельная длина периода запроса расписания в днях, включая обе границы. */
internal const val MAX_SCHEDULE_PERIOD_DAYS = 62

/**
 * Регистрирует маршруты страницы «Расписание».
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.scheduleRoutes(views: ReadViews) {
    route("/schedule") {
        post<ScheduleListRequest, ScheduleListResponse>("/list") { request ->
            val query = request.toQuery()
            db.transaction {
                views.schedule.list(query)
            }
        }
    }
}

/**
 * Преобразует запрос расписания в параметры выборки: проверяет границы периода
 * и превращает пустые списки фильтров в `null`.
 */
context(ctx: RequestContext, raise: Raise<DomainError>)
internal fun ScheduleListRequest.toQuery(): ScheduleQuery {
    if (to < from) {
        raise(CommonDomainError("INVALID_SCHEDULE_PERIOD", Messages.InvalidSchedulePeriod.localize()))
    }
    if (from.daysUntil(to) + 1 > MAX_SCHEDULE_PERIOD_DAYS) {
        raise(CommonDomainError("SCHEDULE_PERIOD_TOO_LONG", Messages.SchedulePeriodTooLong.localize(MAX_SCHEDULE_PERIOD_DAYS)))
    }
    return ScheduleQuery(
        from = from,
        to = to,
        hallIds = hallIds.takeIf { it.isNotEmpty() },
        disciplineIds = disciplineIds.takeIf { it.isNotEmpty() },
        employeeIds = employeeIds.takeIf { it.isNotEmpty() },
    )
}
