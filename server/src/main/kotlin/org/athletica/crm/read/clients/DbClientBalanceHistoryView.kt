package org.athletica.crm.read.clients

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.clients.BalanceJournalEntry
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.api.schemas.common.PerformedBy
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [ClientBalanceHistoryView] поверх PostgreSQL: журнал и имя сотрудника
 * одним запросом, без загрузки агрегатов клиента, баланса и списка сотрудников.
 */
class DbClientBalanceHistoryView : ClientBalanceHistoryView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byClient(clientId: ClientId): ClientBalanceHistoryResponse {
        requireClientExists(clientId)
        val entries =
            tr
                .sql(SQL)
                .bind("clientId", clientId)
                .bind("orgId", ctx.orgId)
                .list { row -> row.toJournalEntry() }
        return ClientBalanceHistoryResponse(entries = entries)
    }

    /**
     * Проверяет, что клиент принадлежит организации: у клиента без операций журнал
     * пуст, и без этой проверки несуществующий идентификатор возвращал бы пустой
     * список вместо ошибки.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun requireClientExists(clientId: ClientId) {
        tr
            .sql("SELECT 1 FROM clients WHERE id = :clientId AND org_id = :orgId")
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { 1 }
            ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))
    }

    context(ctx: EmployeeRequestContext)
    private fun Row.toJournalEntry(): BalanceJournalEntry =
        BalanceJournalEntry(
            id = asUuid("id"),
            amount = asMoney("amount", ctx.currency),
            balanceAfter = asMoney("balance_after", ctx.currency),
            operationType = asString("operation_type"),
            note = asStringOrNull("note"),
            performedBy = performedBy(),
            createdAt = asInstant("created_at"),
        )

    /** Сотрудник операции; `null`, если запись о сотруднике недоступна. */
    private fun Row.performedBy(): PerformedBy? {
        val id = asUuidOrNull("performed_by") ?: return null
        val name = asStringOrNull("performed_by_name") ?: return null
        return PerformedBy(id, name)
    }

    private companion object {
        val SQL =
            """
            SELECT j.id, j.amount, j.balance_after, j.operation_type, j.note, j.created_at,
                   j.performed_by, e.name AS performed_by_name
            FROM client_balance_journal j
            LEFT JOIN employees e ON e.id = j.performed_by AND e.org_id = j.org_id
            WHERE j.client_id = :clientId AND j.org_id = :orgId
            ORDER BY j.created_at DESC
            """.trimIndent()
    }
}
