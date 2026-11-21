package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.memberships.MembershipSaleSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

/** Состояние оплаты абонементов клиента для списка абонементов. */
interface MembershipSalesView {
    /** Продажа, оплата и долг каждого абонемента клиента [clientId] по идентификатору абонемента. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byClient(clientId: ClientId): Map<MembershipId, MembershipSaleSchema>
}
