package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация репозитория интеграций каналов на PostgreSQL через R2DBC. */
class DbChannelIntegrations : ChannelIntegrations {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<ChannelIntegration> =
        tr.sql(
            """
            SELECT id, name, config::text AS config, enabled
            FROM channel_integrations
            WHERE org_id = :orgId
            ORDER BY created_at
            """.trimIndent(),
        )
            .bind("orgId", ctx.orgId)
            .list { row -> row.toChannelIntegration() }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: ChannelIntegrationId): ChannelIntegration =
        tr.sql(
            """
            SELECT id, name, config::text AS config, enabled
            FROM channel_integrations
            WHERE id = :id AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toChannelIntegration() }
            ?: raise(CommonDomainError("CHANNEL_NOT_FOUND", "Интеграция канала не найдена"))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(integration: ChannelIntegration) {
        tr.sql(
            """
            INSERT INTO channel_integrations (id, org_id, channel_type, name, config, enabled)
            VALUES (:id, :orgId, :channelType, :name, :config::jsonb, :enabled)
            """.trimIndent(),
        )
            .bind("id", integration.id)
            .bind("orgId", ctx.orgId)
            .bind("channelType", integration.channelType.name)
            .bind("name", integration.name)
            .bind("config", encodeConfig(integration.config))
            .bind("enabled", integration.enabled)
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(integration: ChannelIntegration) {
        val updated =
            tr.sql(
                """
                UPDATE channel_integrations
                SET channel_type = :channelType, name = :name, config = :config::jsonb, enabled = :enabled
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
                .bind("id", integration.id)
                .bind("orgId", ctx.orgId)
                .bind("channelType", integration.channelType.name)
                .bind("name", integration.name)
                .bind("config", encodeConfig(integration.config))
                .bind("enabled", integration.enabled)
                .execute()

        if (updated == 0L) {
            raise(CommonDomainError("CHANNEL_NOT_FOUND", "Интеграция канала не найдена"))
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(id: ChannelIntegrationId) {
        tr.sql("DELETE FROM channel_integrations WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    context(tr: Transaction)
    override suspend fun configById(id: ChannelIntegrationId): ChannelConfig? =
        tr.sql("SELECT config::text AS config FROM channel_integrations WHERE id = :id")
            .bind("id", id)
            .firstOrNull { row -> decodeConfig(row.asString("config")) }

    private fun Row.toChannelIntegration(): ChannelIntegration =
        ChannelIntegration(
            id = asUuid("id").toChannelIntegrationId(),
            name = asString("name"),
            config = decodeConfig(asString("config")),
            enabled = asBoolean("enabled"),
        )

    private companion object {
        val json = Json

        fun encodeConfig(config: ChannelConfig): String = json.encodeToString(ChannelConfig.serializer(), config)

        fun decodeConfig(text: String): ChannelConfig = json.decodeFromString(ChannelConfig.serializer(), text)
    }
}
