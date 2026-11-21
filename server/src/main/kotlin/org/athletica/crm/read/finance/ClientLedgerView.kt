package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.finance.ClientLedgerResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** История и итог лицевого счёта клиента для сотрудника с правом просмотра баланса. */
interface ClientLedgerView {
    /** Баланс, аванс, долги и история операций клиента [clientId] от новых к старым. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byClient(clientId: ClientId): ClientLedgerResponse
}
