package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.finance.CashAccountBalancesResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Остатки всех доступных сотруднику касс, включая архивные. */
interface CashAccountBalancesView {
    /** Кассы организации из [ctx], доступные сотруднику, с остатками и общим итогом. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun all(): CashAccountBalancesResponse
}
