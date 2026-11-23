package org.athletica.crm.demo

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.storage.Transaction

/**
 * Календарь симулируемого дня: «сегодня» для доменных операций — день истории [day],
 * а не дата часов. Подставляется туда, где операция сама берёт сегодняшнюю дату.
 */
class DemoCalendar(private val day: LocalDate) : OrgCalendar {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun today(): LocalDate = day
}
