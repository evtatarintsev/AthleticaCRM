package org.athletica.crm.routes

import org.athletica.crm.api.schemas.common.PerformedBy
import org.athletica.crm.api.schemas.orgbalance.OrgBalanceDetailResponse
import org.athletica.crm.api.schemas.orgbalance.OrgBalanceJournalEntry
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.employees.Employees
import org.athletica.crm.domain.orgbalance.OrgBalanceEntry
import org.athletica.crm.domain.orgbalance.OrgBalances
import org.athletica.crm.storage.Database

/**
 * Регистрирует маршруты баланса организации:
 * GET /org-balance/detail — текущий баланс и история операций.
 *
 * Имя сотрудника, выполнившего операцию, собирается на уровне route из [Employees]
 * (проекция домена в DTO), а не хранится в самом агрегате [OrgBalanceEntry].
 */
context(db: Database, audit: AuditLog)
fun RouteWithContext.orgBalanceRoutes(orgBalances: OrgBalances, employees: Employees) {
    get<Unit, OrgBalanceDetailResponse>("/org-balance/detail") {
        db.transaction {
            val balance = orgBalances.current()
            val performedById = employees.list().associate { it.id to PerformedBy(it.id.value, it.name) }
            OrgBalanceDetailResponse(
                totalAmount = balance.totalAmount,
                history = balance.history.map { it.toJournalEntry(performedById) },
            )
        }
    }
}

private fun OrgBalanceEntry.toJournalEntry(performedById: Map<EmployeeId, PerformedBy>) =
    OrgBalanceJournalEntry(
        id = id,
        amount = amount,
        balanceAfter = balanceAfter,
        operationType = operationType,
        paymentMethod = paymentMethod,
        description = description,
        performedBy = performedById[performedBy],
        createdAt = createdAt,
    )
