package org.athletica.crm

import org.athletica.crm.core.SystemRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.core.money.Money
import org.athletica.crm.domain.ledger.Ledger
import org.athletica.crm.domain.ledger.LedgerEntries
import org.athletica.crm.domain.ledger.LedgerOwner
import org.athletica.crm.domain.org.DbOrgCalendar

/** Задаёт аванс для теста проекции через настоящую сбалансированную запись. */
suspend fun setClientAdvance(orgId: OrgId, clientId: ClientId, amount: Money) {
    orThrow {
        TestPostgres.db.transaction {
            context(SystemRequestContext(orgId = orgId, currency = amount.currency)) {
                val ledger = Ledger()
                val account = ledger.account(LedgerOwner.Advance(clientId))
                val delta = amount + ledger.balance(account)
                if (!delta.isZero) ledger.record(LedgerEntries.adjustment(account, ledger.account(LedgerOwner.Adjustment), delta), DbOrgCalendar().today())
            }
        }
    }
}
