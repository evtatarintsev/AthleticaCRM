package org.athletica.crm.routes

import org.athletica.crm.api.schemas.orgbalance.OrgBalanceDetailResponse
import org.athletica.crm.api.schemas.orgbalance.OrgBalanceJournalEntry
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.orgbalance.OrgBalanceEntry
import org.athletica.crm.domain.orgbalance.OrgBalances
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты баланса организации:
 * GET /org-balance/detail — текущий баланс и история операций.
 */
context(db: Database, audit: AuditLog)
fun RouteWithContext.orgBalanceRoutes(orgBalances: OrgBalances) {
    get<Unit, OrgBalanceDetailResponse>("/org-balance/detail") {
        db.transaction {
            val balance = orgBalances.current()
            OrgBalanceDetailResponse(
                totalAmount = balance.totalAmount,
                history = balance.history.map { it.toJournalEntry() },
            )
        }
    }
}

private fun OrgBalanceEntry.toJournalEntry() =
    OrgBalanceJournalEntry(
        id = id,
        amount = amount,
        balanceAfter = balanceAfter,
        operationType = operationType,
        description = description,
        createdAt = createdAt,
    )
