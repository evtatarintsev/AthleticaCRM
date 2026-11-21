package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.finance.SaleDetailResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SaleId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Документ продажи с вычисленными суммами и историей оплат, возвратов и выплат. */
interface SaleDetailView {
    /** Продажа из [query]; если её нет в организации из [ctx] — ошибка. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byId(query: SaleDetailQuery): SaleDetailResponse
}

/** Запрос документа продажи. */
data class SaleDetailQuery(
    /** Продажа. */
    val saleId: SaleId,
    /** Сегодняшняя дата организации: кассир может отменить только свою сегодняшнюю оплату. */
    val today: LocalDate,
)
