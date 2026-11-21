package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.finance.CashFlowReportResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.CashAccountId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** ДДС по датам проводок: сторно не переписывает историю предыдущих месяцев. */
interface CashFlowReportView {
    /** Помесячный ДДС по статьям за период [query] с остатками на начало и конец. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun report(query: CashFlowReportQuery): CashFlowReportResponse
}

/** Нормализованный запрос ДДС: период проверен в routes. */
data class CashFlowReportQuery(
    /** Первая дата периода. */
    val from: LocalDate,
    /** Последняя дата периода, не раньше [from]. */
    val to: LocalDate,
    /** Отбор по кассам; пустой список — все доступные. */
    val cashAccountIds: List<CashAccountId> = emptyList(),
)
