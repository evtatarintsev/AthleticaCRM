package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.finance.ClientLedgerResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.permissions.UserPermission.CAN_ACCEPT_PAYMENTS
import org.athletica.crm.core.permissions.requireFinancePermission
import org.athletica.crm.storage.Transaction

/** [ClientPaymentOptionsView] на общем SQL лицевого счёта без истории. */
class DbClientPaymentOptionsView : ClientPaymentOptionsView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byClient(clientId: ClientId): ClientLedgerResponse {
        requireFinancePermission(CAN_ACCEPT_PAYMENTS)
        return clientLedger(clientId, includeHistory = false)
    }
}
