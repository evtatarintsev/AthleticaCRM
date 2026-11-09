package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.messaging.ConversationResponse
import org.athletica.crm.api.schemas.messaging.InboundMessageSchema
import org.athletica.crm.api.schemas.messaging.MessageSchema
import org.athletica.crm.api.schemas.messaging.OutboundMessageSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toMessageId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.MessageDirection
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

class DbConversationView : ConversationView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun messages(clientId: ClientId): ConversationResponse =
        ConversationResponse(
            clientId = clientId,
            messages =
                tr.sql(
                    """
                    SELECT m.id, m.direction, m.body, m.received_via, m.created_at,
                           CASE WHEN m.author_kind = 'EMPLOYEE' THEN m.author_employee_id END AS author_employee_id,
                           COALESCE((
                               SELECT jsonb_agg(jsonb_build_object(
                                   'channelIntegrationId', d.channel_integration_id,
                                   'state', CASE WHEN d.state IN ('PENDING', 'SENT', 'DELIVERED') THEN d.state ELSE 'FAILED' END,
                                   'errorMessage', CASE WHEN d.state NOT IN ('PENDING', 'SENT', 'DELIVERED')
                                                       THEN COALESCE(d.error_message, '') END
                               ) ORDER BY d.created_at, d.id)
                               FROM message_deliveries d
                               WHERE d.message_id = m.id AND d.org_id = m.org_id
                           ), '[]'::jsonb) AS deliveries
                    FROM conversations c
                    JOIN messages m ON m.conversation_id = c.id AND m.org_id = c.org_id
                    WHERE c.org_id = :orgId AND c.client_id = :clientId
                    ORDER BY m.created_at, m.id
                    """.trimIndent(),
                )
                    .bind("orgId", ctx.orgId)
                    .bind("clientId", clientId)
                    .list { it.toMessageSchema() },
        )

    private fun Row.toMessageSchema(): MessageSchema {
        val id = asUuid("id").toMessageId()
        val body = asString("body")
        val createdAt = asInstant("created_at").toString()
        return when (MessageDirection.valueOf(asString("direction"))) {
            MessageDirection.OUTBOUND ->
                OutboundMessageSchema(
                    id = id,
                    body = body,
                    createdAt = createdAt,
                    authorEmployeeId = asUuidOrNull("author_employee_id")?.toEmployeeId(),
                    deliveries = Json.decodeFromString(asString("deliveries")),
                )

            MessageDirection.INBOUND ->
                InboundMessageSchema(
                    id = id,
                    body = body,
                    createdAt = createdAt,
                    receivedVia = asUuid("received_via").toChannelIntegrationId(),
                )
        }
    }
}
