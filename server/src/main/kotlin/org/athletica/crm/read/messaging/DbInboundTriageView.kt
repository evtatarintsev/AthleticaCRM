package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.messaging.InboundReceiptSchema
import org.athletica.crm.api.schemas.messaging.TriageListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.entityids.toInboundReceiptId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [InboundTriageView] поверх PostgreSQL. */
class DbInboundTriageView : InboundTriageView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): TriageListResponse =
        TriageListResponse(
            tr
                .sql(
                    """
                    SELECT id, integration_id, from_address, body, received_at
                    FROM inbound_receipts
                    WHERE org_id = :orgId AND state = 'UNRESOLVED'
                    ORDER BY received_at
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row -> row.toSchema() },
        )

    /** Собирает элемент очереди из строки выборки. */
    private fun Row.toSchema(): InboundReceiptSchema =
        InboundReceiptSchema(
            id = asUuid("id").toInboundReceiptId(),
            integrationId = asUuid("integration_id").toChannelIntegrationId(),
            fromAddress = asString("from_address"),
            body = asString("body"),
            receivedAt = asInstant("received_at").toString(),
        )
}
