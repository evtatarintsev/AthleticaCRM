package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.finance.ClientLedgerResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Аванс и долги клиента для блока оплаты; история операций кассиру не отдаётся. */
interface ClientPaymentOptionsView {
    /** Баланс, аванс и продажи с долгом клиента [clientId] без истории операций. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byClient(clientId: ClientId): ClientLedgerResponse
}
