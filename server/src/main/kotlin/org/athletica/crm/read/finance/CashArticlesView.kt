package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.finance.CashArticlesResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Справочник статей ДДС для форм и фильтров. */
interface CashArticlesView {
    /** Все статьи организации из [ctx], включая архивные и системные. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun all(): CashArticlesResponse
}
