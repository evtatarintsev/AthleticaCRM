package org.athletica.crm.domain.conversations

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.entityids.toConversationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asUuid
import kotlin.time.Instant

/** Реализация read horizon диалогов на PostgreSQL через R2DBC. */
class DbConversationReadStates : ConversationReadStates {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun markRead(conversationId: ConversationId, at: Instant) {
        tr.sql(
            """
            INSERT INTO conversation_read_state (conversation_id, org_id, last_read_at)
            VALUES (:conversationId, :orgId, :at)
            ON CONFLICT (conversation_id) DO UPDATE SET last_read_at = excluded.last_read_at
            """.trimIndent(),
        )
            .bind("conversationId", conversationId)
            .bind("orgId", ctx.orgId)
            .bind("at", at)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byConversation(conversationId: ConversationId): ConversationReadState? =
        tr.sql(
            """
            SELECT conversation_id, last_read_at
            FROM conversation_read_state
            WHERE conversation_id = :conversationId AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("conversationId", conversationId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                ConversationReadState(
                    conversationId = row.asUuid("conversation_id").toConversationId(),
                    lastReadAt = row.asInstant("last_read_at"),
                )
            }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun unreadCount(conversationId: ConversationId): Long =
        tr.sql(
            """
            SELECT count(*) AS unread
            FROM messages m
            WHERE m.conversation_id = :conversationId
              AND m.org_id = :orgId
              AND m.direction = 'INBOUND'
              AND m.created_at > COALESCE(
                  (SELECT last_read_at FROM conversation_read_state WHERE conversation_id = :conversationId),
                  'epoch'::timestamptz
              )
            """.trimIndent(),
        )
            .bind("conversationId", conversationId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.asLong("unread") } ?: 0L
}
