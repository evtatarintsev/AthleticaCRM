package org.athletica.crm.domain.inbound

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.InboundReceiptId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.entityids.toInboundReceiptId
import org.athletica.crm.core.entityids.toMessageId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Clock

/** Реализация инбокса входящих на PostgreSQL через R2DBC. */
class DbInboundReceipts : InboundReceipts {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun record(integrationId: ChannelIntegrationId, fromAddress: String, body: String): InboundReceipt {
        val id = InboundReceiptId.new()
        val now = Clock.System.now()
        tr.sql(
            """
            INSERT INTO inbound_receipts (id, org_id, integration_id, from_address, body, state, received_at)
            VALUES (:id, :orgId, :integrationId, :fromAddress, :body, 'UNRESOLVED', :now)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("integrationId", integrationId)
            .bind("fromAddress", fromAddress)
            .bind("body", body)
            .bind("now", now)
            .execute()

        return InboundReceipt(
            id = id,
            integrationId = integrationId,
            fromAddress = fromAddress,
            body = body,
            state = InboundReceiptState.UNRESOLVED,
            resolvedMessageId = null,
            receivedAt = now,
        )
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: InboundReceiptId): InboundReceipt? =
        tr.sql(
            """
            SELECT id, integration_id, from_address, body, state, resolved_message_id, received_at
            FROM inbound_receipts
            WHERE id = :id AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toReceipt() }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun markResolved(id: InboundReceiptId, messageId: MessageId) {
        tr.sql(
            """
            UPDATE inbound_receipts
            SET state = 'RESOLVED', resolved_message_id = :messageId
            WHERE id = :id AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("messageId", messageId)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    private fun Row.toReceipt(): InboundReceipt =
        InboundReceipt(
            id = asUuid("id").toInboundReceiptId(),
            integrationId = asUuid("integration_id").toChannelIntegrationId(),
            fromAddress = asString("from_address"),
            body = asString("body"),
            state = InboundReceiptState.valueOf(asString("state")),
            resolvedMessageId = asUuidOrNull("resolved_message_id")?.toMessageId(),
            receivedAt = asInstant("received_at"),
        )
}
