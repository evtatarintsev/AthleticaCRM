package org.athletica.crm.domain.messagedelivery

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.DeliveryId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.entityids.toDeliveryId
import org.athletica.crm.core.entityids.toMessageId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.time.Clock

/** Реализация репозитория доставок на PostgreSQL через R2DBC. */
class DbDeliveries : Deliveries {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(
        messageId: MessageId,
        channelIntegrationId: ChannelIntegrationId,
        recipientAddress: String,
    ): Delivery {
        val id = DeliveryId.new()
        val now = Clock.System.now()
        tr.sql(
            """
            INSERT INTO message_deliveries (
                id, org_id, message_id, channel_integration_id, recipient_address, state, attempts, created_at
            )
            VALUES (:id, :orgId, :messageId, :channelIntegrationId, :recipientAddress, 'PENDING', 0, :now)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("messageId", messageId)
            .bind("channelIntegrationId", channelIntegrationId)
            .bind("recipientAddress", recipientAddress)
            .bind("now", now)
            .execute()

        return Delivery(
            id = id,
            messageId = messageId,
            channelIntegrationId = channelIntegrationId,
            recipientAddress = recipientAddress,
            state = DeliveryState.Pending,
            providerRef = null,
            attempts = 0,
            createdAt = now,
        )
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byMessage(messageId: MessageId): List<Delivery> =
        tr.sql(
            """
            SELECT id, message_id, channel_integration_id, recipient_address, state,
                   provider_message_ref, error_code, error_message, attempts, created_at
            FROM message_deliveries
            WHERE message_id = :messageId AND org_id = :orgId
            ORDER BY created_at
            """.trimIndent(),
        )
            .bind("messageId", messageId)
            .bind("orgId", ctx.orgId)
            .list { row -> row.toDelivery() }

    context(tr: Transaction)
    override suspend fun pending(limit: Int): List<PendingDelivery> =
        tr.sql(
            """
            SELECT d.id, d.channel_integration_id, d.recipient_address, m.body, d.attempts
            FROM message_deliveries d
            JOIN messages m ON m.id = d.message_id
            JOIN channel_integrations c ON c.id = d.channel_integration_id
            WHERE d.state = 'PENDING' AND c.enabled = true
            ORDER BY d.created_at
            LIMIT :limit
            """.trimIndent(),
        )
            .bind("limit", limit)
            .list { row ->
                PendingDelivery(
                    id = row.asUuid("id").toDeliveryId(),
                    channelIntegrationId = row.asUuid("channel_integration_id").toChannelIntegrationId(),
                    recipientAddress = row.asString("recipient_address"),
                    body = row.asString("body"),
                    attempts = row.asInt("attempts"),
                )
            }

    context(tr: Transaction)
    override suspend fun markSent(id: DeliveryId, ref: ProviderMessageRef) {
        tr.sql(
            """
            UPDATE message_deliveries
            SET state = 'SENT', provider_message_ref = :ref, sent_at = now(), error_code = NULL, error_message = NULL
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("ref", ref.value)
            .execute()
    }

    context(tr: Transaction)
    override suspend fun markFailed(id: DeliveryId, error: DeliveryError) {
        tr.sql(
            """
            UPDATE message_deliveries
            SET state = 'FAILED', error_code = :code, error_message = :message
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("code", error.code)
            .bind("message", error.message)
            .execute()
    }

    context(tr: Transaction)
    override suspend fun retryLater(id: DeliveryId, error: DeliveryError) {
        tr.sql(
            """
            UPDATE message_deliveries
            SET attempts = attempts + 1, error_code = :code, error_message = :message
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("code", error.code)
            .bind("message", error.message)
            .execute()
    }

    private fun Row.toDelivery(): Delivery =
        Delivery(
            id = asUuid("id").toDeliveryId(),
            messageId = asUuid("message_id").toMessageId(),
            channelIntegrationId = asUuid("channel_integration_id").toChannelIntegrationId(),
            recipientAddress = asString("recipient_address"),
            state = readState(),
            providerRef = asStringOrNull("provider_message_ref")?.let { ProviderMessageRef(it) },
            attempts = asInt("attempts"),
            createdAt = asInstant("created_at"),
        )

    private fun Row.readState(): DeliveryState =
        when (asString("state")) {
            "PENDING" -> DeliveryState.Pending
            "SENT" -> DeliveryState.Sent
            "DELIVERED" -> DeliveryState.Delivered
            else ->
                DeliveryState.Failed(
                    DeliveryError(
                        code = asStringOrNull("error_code").orEmpty(),
                        message = asStringOrNull("error_message").orEmpty(),
                    ),
                )
        }
}
