package org.athletica.crm.usecases.clients

import arrow.core.Either
import arrow.core.raise.either
import kotlinx.datetime.Instant
import org.athletica.crm.api.schemas.clients.BalanceJournalEntry
import org.athletica.crm.api.schemas.clients.ClientBalanceHistoryResponse
import org.athletica.crm.api.schemas.clients.PerformedBy
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

context(db: Database, ctx: RequestContext)
suspend fun clientBalanceHistory(clientId: Uuid): Either<CommonDomainError, ClientBalanceHistoryResponse> =
    either {
        // Проверяем принадлежность клиента организации
        db
            .sql("SELECT 1 FROM clients WHERE id = :id AND org_id = :orgId")
            .bind("id", clientId)
            .bind("orgId", ctx.orgId.value)
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
                .bind("orgId", ctx.orgId.value)
                .list { row ->
                    val performedById = row.get("performed_by_id", java.util.UUID::class.java)
                    val performedByName = row.get("performed_by_name", String::class.java)
                    BalanceJournalEntry(
                        id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                        amount = row.get("amount", java.math.BigDecimal::class.java)!!.toDouble(),
                        balanceAfter = row.get("balance_after", java.math.BigDecimal::class.java)!!.toDouble(),
                        operationType = row.get("operation_type", String::class.java)!!,
                        note = row.get("note", String::class.java),
                        performedBy =
                            if (performedById != null && performedByName != null) {
                                PerformedBy(id = performedById.toKotlinUuid(), name = performedByName)
                            } else {
                                null
                            },
                        createdAt =
                            row
                                .get("created_at", java.time.OffsetDateTime::class.java)!!
                                .toInstant()
                                .let { Instant.fromEpochMilliseconds(it.toEpochMilli()) },
                    )
                }

        ClientBalanceHistoryResponse(entries)
    }
