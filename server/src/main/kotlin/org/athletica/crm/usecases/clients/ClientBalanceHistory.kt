package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import org.athletica.crm.api.schemas.clients.BalanceJournalEntry
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.api.schemas.clients.PerformedBy
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.db.asDouble
import org.athletica.crm.db.asInstant
import org.athletica.crm.db.asString
import org.athletica.crm.db.asStringOrNull
import org.athletica.crm.db.asUuid
import org.athletica.crm.db.asUuidOrNull
import org.athletica.crm.core.ClientId
import org.athletica.crm.i18n.Messages

context(db: Database, ctx: RequestContext)
suspend fun clientBalanceHistory(clientId: ClientId): Either<CommonDomainError, ClientBalanceHistoryResponse> =
    either {
        // Проверяем принадлежность клиента организации
        db
            .sql("SELECT 1 FROM clients WHERE id = :id AND org_id = :orgId")
            .bind("id", clientId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { true }
            ?: raise(CommonDomainError("CLIENT_NOT_FOUND", Messages.ClientNotFound.localize()))

        val entries =
            db
                .sql(
                    """
                    SELECT j.id,
                           j.amount,
                           j.balance_after,
                           j.operation_type,
                           j.note,
                           j.created_at,
                           j.performed_by  AS performed_by_id,
                           e.name          AS performed_by_name
                    FROM client_balance_journal j
                    LEFT JOIN employees e ON e.user_id = j.performed_by AND e.org_id = j.org_id
                    WHERE j.client_id = :clientId AND j.org_id = :orgId
                    ORDER BY j.created_at DESC
                    """.trimIndent(),
                )
                .bind("clientId", clientId)
                .bind("orgId", ctx.orgId)
                .list { row ->
                    val performedById = row.asUuidOrNull("performed_by_id")
                    val performedByName = row.asStringOrNull("performed_by_name")
                    BalanceJournalEntry(
                        id = row.asUuid("id"),
                        amount = row.asDouble("amount"),
                        balanceAfter = row.asDouble("balance_after"),
                        operationType = row.asString("operation_type"),
                        note = row.asStringOrNull("note"),
                        performedBy =
                            if (performedById != null && performedByName != null) {
                                PerformedBy(id = performedById, name = performedByName)
                            } else {
                                null
                            },
                        createdAt = row.asInstant("created_at"),
                    )
                }

        ClientBalanceHistoryResponse(entries)
    }
