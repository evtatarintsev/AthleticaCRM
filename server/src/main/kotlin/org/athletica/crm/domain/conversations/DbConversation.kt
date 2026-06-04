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
import org.athletica.crm.core.messaging.MessageDirection
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Clock

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
    override suspend fun appendOutbound(author: Author, body: String): OutboundMessage {
        val messageId = MessageId.new()
        val now = Clock.System.now()
        val (authorKind, authorEmployeeId) =
            when (author) {
                is Author.Employee -> "EMPLOYEE" to author.id
                Author.System -> "SYSTEM" to null
            }
        tr.sql(
            """
            INSERT INTO messages (id, org_id, conversation_id, direction, body, author_kind, author_employee_id, created_at)
            VALUES (:id, :orgId, :conversationId, :direction, :body, :authorKind, :authorEmployeeId, :now)
            """.trimIndent(),
        )
            .bind("id", messageId)
            .bind("orgId", ctx.orgId)
            .bind("conversationId", id)
            .bind("direction", MessageDirection.OUTBOUND.name)
            .bind("body", body)
            .bind("authorKind", authorKind)
            .bind("authorEmployeeId", authorEmployeeId)
            .bind("now", now)
            .execute()

        return OutboundMessage(
            id = messageId,
            conversationId = id,
            body = body,
            createdAt = now,
            author = author,
        )
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun appendInbound(receivedVia: ChannelIntegrationId, body: String): InboundMessage {
        val messageId = MessageId.new()
        val now = Clock.System.now()
        tr.sql(
            """
            INSERT INTO messages (id, org_id, conversation_id, direction, body, received_via, created_at)
            VALUES (:id, :orgId, :conversationId, :direction, :body, :receivedVia, :now)
            """.trimIndent(),
        )
            .bind("id", messageId)
            .bind("orgId", ctx.orgId)
            .bind("conversationId", id)
            .bind("direction", MessageDirection.INBOUND.name)
            .bind("body", body)
            .bind("receivedVia", receivedVia)
            .bind("now", now)
            .execute()

        return InboundMessage(
            id = messageId,
            conversationId = id,
            body = body,
            createdAt = now,
            receivedVia = receivedVia,
        )
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun messages(): List<Message> =
        tr.sql(
            """
            SELECT id, conversation_id, direction, body, author_kind, author_employee_id, received_via, created_at
            FROM messages
            WHERE conversation_id = :conversationId AND org_id = :orgId
            ORDER BY created_at
            """.trimIndent(),
        )
            .bind("conversationId", id)
            .bind("orgId", ctx.orgId)
            .list { row -> row.toMessage() }

    private fun Row.toMessage(): Message {
        val id = asUuid("id").toMessageId()
        val conversationId = asUuid("conversation_id").toConversationId()
        val body = asString("body")
        val createdAt = asInstant("created_at")
        return when (MessageDirection.valueOf(asString("direction"))) {
            MessageDirection.OUTBOUND ->
                OutboundMessage(
                    id = id,
                    conversationId = conversationId,
                    body = body,
                    createdAt = createdAt,
                    author =
                        when (asString("author_kind")) {
                            "EMPLOYEE" -> Author.Employee(asUuid("author_employee_id").toEmployeeId())
                            else -> Author.System
                        },
                )

            MessageDirection.INBOUND ->
                InboundMessage(
                    id = id,
                    conversationId = conversationId,
                    body = body,
                    createdAt = createdAt,
                    receivedVia = asUuidOrNull("received_via")!!.toChannelIntegrationId(),
                )
        }
    }
}
