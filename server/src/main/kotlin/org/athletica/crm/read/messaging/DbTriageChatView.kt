package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.messaging.TriageChatMessagesResponse
import org.athletica.crm.api.schemas.messaging.TriageMessageSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.toInboundReceiptId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [TriageChatView] поверх PostgreSQL. */
class DbTriageChatView : TriageChatView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun messages(integrationId: ChannelIntegrationId, address: String): TriageChatMessagesResponse =
        TriageChatMessagesResponse(
            tr
                .sql(
                    """
                    SELECT id, body, received_at
                    FROM inbound_receipts
                    WHERE org_id = :orgId AND integration_id = :integrationId
                      AND from_address = :address AND state = 'UNRESOLVED'
                    ORDER BY received_at, id
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("integrationId", integrationId)
                .bind("address", address)
                .list { row ->
                    TriageMessageSchema(
                        id = row.asUuid("id").toInboundReceiptId(),
                        body = row.asString("body"),
                        receivedAt = row.asInstant("received_at").toString(),
                    )
                },
        )
}
