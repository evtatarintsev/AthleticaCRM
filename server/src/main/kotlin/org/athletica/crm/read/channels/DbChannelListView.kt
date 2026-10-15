package org.athletica.crm.read.channels

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.channels.ChannelIntegrationSchema
import org.athletica.crm.api.schemas.channels.ChannelListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [ChannelListView] поверх PostgreSQL. */
class DbChannelListView : ChannelListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): ChannelListResponse =
        ChannelListResponse(
            tr
                .sql(
                    """
                    SELECT id, name, config::text AS config, enabled
                    FROM channel_integrations
                    WHERE org_id = :orgId
                    ORDER BY created_at
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .list { row -> row.toSchema() },
        )

    /** Собирает элемент списка из строки выборки. */
    private fun Row.toSchema(): ChannelIntegrationSchema =
        ChannelIntegrationSchema(
            id = asUuid("id").toChannelIntegrationId(),
            name = asString("name"),
            config = Json.decodeFromString(ChannelConfig.serializer(), asString("config")),
            enabled = asBoolean("enabled"),
        )
}
