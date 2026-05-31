package org.athletica.crm.domain.conversations

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.entityids.EmployeeId
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
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Диалог с клиентом: агрегирует сообщения по всем каналам. Один на клиента. */
class DbConversation(
    override val id: ConversationId,
) : Conversation {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun touch() {
        tr.sql("UPDATE conversations SET last_message_at = now() WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun enqueue(
        channelIntegrationId: ChannelIntegrationId?,
        channelType: ChannelType,
        recipientAddress: String?,
        body: String,
    ): Message {
        val messageId = MessageId.new()
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
            .bind("conversationId", id)
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
            id = messageId,
            conversationId = id,
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
    override suspend fun messages(): List<Message> =
        tr.sql(
            """
            SELECT id, conversation_id, channel_integration_id, channel_type, direction,
                   sender_kind, sender_employee_id, recipient_address, body, status, error_message, created_at
            FROM messages
            WHERE conversation_id = :conversationId AND org_id = :orgId
            ORDER BY created_at
            """.trimIndent(),
        )
            .bind("conversationId", id)
            .bind("orgId", ctx.orgId)
            .list { row -> row.toMessage() }

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

interface Conversation {
    val id: ConversationId

    /**
     * Ставит исходящее сообщение в очередь (`QUEUED`) от лица текущего сотрудника (`ADMIN`).
     * [channelIntegrationId] — интеграция, через которую пойдёт отправка; [recipientAddress] —
     * адрес получателя (`null` для IN_APP). Возвращает созданное сообщение.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun enqueue(
        channelIntegrationId: ChannelIntegrationId?,
        channelType: ChannelType,
        recipientAddress: String?,
        body: String,
    ): Message

    /** Обновляет момент последнего сообщения диалога на «сейчас». */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun touch()

    /** Возвращает все сообщения диалога в порядке от старых к новым. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun messages(): List<Message>
}

/**
 * Сообщение в диалоге.
 *
 * Ссылки на соседние агрегаты — только по идентификатору ([senderEmployeeId], [channelIntegrationId]):
 * человекочитаемое представление собирает слой routes/projection.
 */
data class Message(
    val id: MessageId,
    val conversationId: ConversationId,
    val channelIntegrationId: ChannelIntegrationId?,
    val channelType: ChannelType,
    val direction: MessageDirection,
    val senderKind: SenderKind,
    val senderEmployeeId: EmployeeId?,
    val recipientAddress: String?,
    val body: String,
    val status: MessageStatus,
    val errorMessage: String?,
    val createdAt: Instant,
)
