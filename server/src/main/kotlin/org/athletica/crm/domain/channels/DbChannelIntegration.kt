package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.storage.Transaction

/** R2DBC-реализация интеграции канала. Все запросы изолированы по `org_id`. */
data class DbChannelIntegration(
    override val id: ChannelIntegrationId,
    override val name: String,
    override val config: ChannelConfig,
    override val enabled: Boolean,
) : ChannelIntegration {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        tr.sql(
            """
            INSERT INTO channel_integrations (id, org_id, channel_type, name, config, enabled)
            VALUES (:id, :orgId, :channelType, :name, :config::jsonb, :enabled)
            ON CONFLICT (id) DO UPDATE SET
                channel_type = EXCLUDED.channel_type,
                name         = EXCLUDED.name,
                config       = EXCLUDED.config,
                enabled      = EXCLUDED.enabled
            WHERE channel_integrations.org_id = :orgId
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("channelType", channelType.name)
            .bind("name", name)
            .bind("config", encodeChannelConfig(config))
            .bind("enabled", enabled)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() {
        tr.sql("DELETE FROM channel_integrations WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    override fun withNew(name: String, config: ChannelConfig, enabled: Boolean): ChannelIntegration = copy(name = name, config = config, enabled = enabled)
}
