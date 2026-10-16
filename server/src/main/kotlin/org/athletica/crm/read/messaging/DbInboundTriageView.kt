package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.messaging.TriageChatSchema
import org.athletica.crm.api.schemas.messaging.TriageListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/** Реализация [InboundTriageView] поверх PostgreSQL. */
class DbInboundTriageView : InboundTriageView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): TriageListResponse =
        TriageListResponse(
            tr
                .sql(
                    """
                    SELECT r.integration_id, r.from_address, ci.name AS channel_name,
                           COALESCE(cc.display_name, '') AS display_name, cc.username, cc.phone,
                           r.unresolved_count, r.last_body, r.last_at
                    FROM (
                        SELECT integration_id, from_address,
                               count(*) AS unresolved_count,
                               max(received_at) AS last_at,
                               (array_agg(body ORDER BY received_at DESC, id DESC))[1] AS last_body
                        FROM inbound_receipts
                        WHERE org_id = :orgId AND state = 'UNRESOLVED'
                        GROUP BY integration_id, from_address
                    ) r
                    JOIN channel_integrations ci ON ci.id = r.integration_id AND ci.org_id = :orgId
                    LEFT JOIN channel_chats cc ON cc.integration_id = r.integration_id AND cc.address = r.from_address
                    ORDER BY r.last_at DESC
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row -> row.toSchema() },
        )

    /** Собирает запись очереди из строки выборки. */
    private fun Row.toSchema(): TriageChatSchema =
        TriageChatSchema(
            integrationId = asUuid("integration_id").toChannelIntegrationId(),
            channelName = asString("channel_name"),
            address = asString("from_address"),
            displayName = asString("display_name"),
            username = asStringOrNull("username"),
            phone = asStringOrNull("phone"),
            unresolvedCount = asLong("unresolved_count").toInt(),
            lastMessageBody = asString("last_body"),
            lastMessageAt = asInstant("last_at").toString(),
        )
}
