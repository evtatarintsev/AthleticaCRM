package org.athletica.crm.read.finance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.money.Money
import org.athletica.crm.storage.Transaction

/** Баланс клиента = аванс − долги; отсутствие проводок даёт ноль. */
interface ClientBalanceView {
    /** Баланс клиента [clientId] организации из [ctx]; если клиента нет — ошибка. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun byClient(clientId: ClientId): Money
}
