package org.athletica.crm.domain.conversations

import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.entityids.toMessageId
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/** Реализация репозитория сообщений на PostgreSQL через R2DBC. */
class DbMessages : Messages {
    context(tr: Transaction)
    suspend fun pendingOutbound(limit: Int): List<PendingMessage> =
        tr.sql(
            """
            SELECT id, channel_type, channel_integration_id, recipient_address, body, retry_count
            FROM messages
            WHERE status = 'QUEUED' AND direction = 'OUTBOUND'
            ORDER BY created_at
            LIMIT :limit
            """.trimIndent(),
        )
            .bind("limit", limit)
            .list { row ->
                PendingMessage(
                    id = row.asUuid("id").toMessageId(),
                    channelType = ChannelType.valueOf(row.asString("channel_type")),
                    channelIntegrationId = row.asUuidOrNull("channel_integration_id")?.toChannelIntegrationId(),
                    recipientAddress = row.asStringOrNull("recipient_address"),
                    body = row.asString("body"),
                    retryCount = row.asInt("retry_count"),
                )
            }

    context(tr: Transaction)
    override suspend fun markSent(id: MessageId, ref: ProviderMessageRef) {
        tr.sql(
            """
            UPDATE messages
            SET status = 'SENT', provider_message_ref = :ref, sent_at = now(), error_code = NULL, error_message = NULL
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("ref", ref.value)
            .execute()
    }

    context(tr: Transaction)
    override suspend fun markFailed(id: MessageId, code: String, message: String) {
        tr.sql(
            """
            UPDATE messages
            SET status = 'FAILED', error_code = :code, error_message = :message
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("code", code)
            .bind("message", message)
            .execute()
    }

    context(tr: Transaction)
    override suspend fun retryLater(id: MessageId, error: String) {
        tr.sql(
            """
            UPDATE messages
            SET retry_count = retry_count + 1, error_message = :error
            WHERE id = :id
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("error", error)
            .execute()
    }
}
