package org.athletica.crm.domain.conversations

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.entityids.MessageId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.entityids.toConversationId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toMessageId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.core.messaging.MessageDirection
import org.athletica.crm.core.messaging.MessageStatus
import org.athletica.crm.core.messaging.SenderKind
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Clock

/** Реализация репозитория сообщений на PostgreSQL через R2DBC. */
class DbMessages : Messages {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun enqueue(
        conversationId: ConversationId,
        channelIntegrationId: ChannelIntegrationId?,
        channelType: ChannelType,
        recipientAddress: String?,
        body: String,
    ): Message {
        val id = MessageId.new()
        val now = Clock.System.now()
        tr.sql(
            """
            INSERT INTO messages (
                id, org_id, conversation_id, channel_integration_id, channel_type,
                direction, sender_kind, sender_employee_id, recipient_address, body, status, created_at
            )
            VALUES (
                :id, :orgId, :conversationId, :channelIntegrationId, :channelType,
                :direction, :senderKind, :senderEmployeeId, :recipientAddress, :body, :status, :now
            )
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("conversationId", conversationId)
            .bind("channelIntegrationId", channelIntegrationId)
            .bind("channelType", channelType.name)
            .bind("direction", MessageDirection.OUTBOUND.name)
            .bind("senderKind", SenderKind.ADMIN.name)
            .bind("senderEmployeeId", ctx.employeeId)
            .bind("recipientAddress", recipientAddress)
            .bind("body", body)
            .bind("status", MessageStatus.QUEUED.name)
            .bind("now", now)
            .execute()

        return Message(
            id = id,
            conversationId = conversationId,
            channelIntegrationId = channelIntegrationId,
            channelType = channelType,
            direction = MessageDirection.OUTBOUND,
            senderKind = SenderKind.ADMIN,
            senderEmployeeId = ctx.employeeId,
            recipientAddress = recipientAddress,
            body = body,
            status = MessageStatus.QUEUED,
            errorMessage = null,
            createdAt = now,
        )
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byConversation(conversationId: ConversationId): List<Message> =
        tr.sql(
            """
            SELECT id, conversation_id, channel_integration_id, channel_type, direction,
                   sender_kind, sender_employee_id, recipient_address, body, status, error_message, created_at
            FROM messages
            WHERE conversation_id = :conversationId AND org_id = :orgId
            ORDER BY created_at
            """.trimIndent(),
        )
            .bind("conversationId", conversationId)
            .bind("orgId", ctx.orgId)
            .list { row -> row.toMessage() }

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

    private fun Row.toMessage(): Message =
        Message(
            id = asUuid("id").toMessageId(),
            conversationId = asUuid("conversation_id").toConversationId(),
            channelIntegrationId = asUuidOrNull("channel_integration_id")?.toChannelIntegrationId(),
            channelType = ChannelType.valueOf(asString("channel_type")),
            direction = MessageDirection.valueOf(asString("direction")),
            senderKind = SenderKind.valueOf(asString("sender_kind")),
            senderEmployeeId = asUuidOrNull("sender_employee_id")?.toEmployeeId(),
            recipientAddress = asStringOrNull("recipient_address"),
            body = asString("body"),
            status = MessageStatus.valueOf(asString("status")),
            errorMessage = asStringOrNull("error_message"),
            createdAt = asInstant("created_at"),
        )
}
