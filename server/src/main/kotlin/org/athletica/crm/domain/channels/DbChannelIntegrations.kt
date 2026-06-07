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
    override suspend fun new(id: ChannelIntegrationId, name: String, config: ChannelConfig): ChannelIntegration = DbChannelIntegration(id = id, name = name, config = config, enabled = true)

    context(tr: Transaction)
    override suspend fun configById(id: ChannelIntegrationId): ChannelConfig? =
        tr.sql("SELECT config::text AS config FROM channel_integrations WHERE id = :id")
            .bind("id", id)
            .firstOrNull { row -> decodeChannelConfig(row.asString("config")) }
}

/** Собирает доменную интеграцию из строки результата. */
internal fun Row.toChannelIntegration(): ChannelIntegration =
    DbChannelIntegration(
        id = asUuid("id").toChannelIntegrationId(),
        name = asString("name"),
        config = decodeChannelConfig(asString("config")),
        enabled = asBoolean("enabled"),
    )

/** Сериализует конфиг канала для хранения в jsonb-колонке. */
internal fun encodeChannelConfig(config: ChannelConfig): String = Json.encodeToString(ChannelConfig.serializer(), config)

/** Десериализует конфиг канала из текстового представления jsonb-колонки. */
internal fun decodeChannelConfig(text: String): ChannelConfig = Json.decodeFromString(ChannelConfig.serializer(), text)
