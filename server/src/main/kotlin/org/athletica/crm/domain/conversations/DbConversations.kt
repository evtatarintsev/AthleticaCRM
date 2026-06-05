package org.athletica.crm.domain.conversations

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.entityids.toConversationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asUuid

/** Реализация репозитория диалогов на PostgreSQL через R2DBC. */
class DbConversations : Conversations {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun forClient(clientId: ClientId): Conversation {
        val existing =
            tr.sql(
                "SELECT id, client_id FROM conversations WHERE client_id = :clientId AND org_id = :orgId",
            )
                .bind("clientId", clientId)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row ->
                    DbConversation(
                        id = row.asUuid("id").toConversationId(),
                    )
                }

        if (existing != null) {
            return existing
        }

        val id = ConversationId.new()
        tr.sql(
            """
            INSERT INTO conversations (id, org_id, client_id)
            VALUES (:id, :orgId, :clientId)
            ON CONFLICT (org_id, client_id) DO NOTHING
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("clientId", clientId)
            .execute()

        return tr.sql("SELECT id, client_id FROM conversations WHERE client_id = :clientId AND org_id = :orgId")
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                DbConversation(
                    id = row.asUuid("id").toConversationId(),
                )
            }!!
    }
}
