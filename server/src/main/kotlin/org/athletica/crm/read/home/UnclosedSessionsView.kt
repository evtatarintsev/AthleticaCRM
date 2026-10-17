package org.athletica.crm.read.home

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.home.UnclosedSessionsResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import kotlin.time.Instant

/**
 * Read-проекция для виджета главной «Незакрытые журналы»: занятия текущего филиала,
 * которые по расписанию уже закончились, но остались запланированными — журнал не закрыт.
 */
interface UnclosedSessionsView {
    /** Возвращает незакрытые занятия по запросу [query]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun unclosed(query: UnclosedSessionsQuery): UnclosedSessionsResponse
}

/**
 * Запрос незакрытых занятий: закончившиеся к моменту [now] (окончание занятия — в часовом поясе
 * организации), не больше [limit] строк.
 */
data class UnclosedSessionsQuery(
    /** Момент, к которому занятие должно закончиться. */
    val now: Instant,
    /** Максимум строк в ответе. */
    val limit: Int,
)
