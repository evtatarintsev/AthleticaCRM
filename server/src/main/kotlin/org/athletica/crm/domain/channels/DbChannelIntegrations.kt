package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
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

/**
 * Реализация коллекции интеграций каналов на PostgreSQL через R2DBC.
 * [factory] собирает строку результата в конкретную интеграцию (адаптер провайдера).
 */
class DbChannelIntegrations(
    private val factory: ChannelIntegrationFactory,
) : ChannelIntegrations {
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
            .firstOrNull { row -> row.toChannelIntegration(factory) }
            ?: raise(CommonDomainError("CHANNEL_NOT_FOUND", "Интеграция канала не найдена"))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun add(id: ChannelIntegrationId, name: String, config: ChannelConfig) {
        tr.sql(
            """
            INSERT INTO channel_integrations (id, org_id, name, config, enabled)
            VALUES (:id, :orgId, :name, :config::jsonb, true)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("name", name)
            .bind("config", encodeChannelConfig(config))
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(
        id: ChannelIntegrationId,
        name: String,
        config: ChannelConfig,
        enabled: Boolean,
    ) {
        val updated =
            tr.sql(
                """
                UPDATE channel_integrations
                SET name = :name, config = :config::jsonb, enabled = :enabled
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("name", name)
                .bind("config", encodeChannelConfig(config))
                .bind("enabled", enabled)
                .execute()
        ensure(updated != 0L) { CommonDomainError("CHANNEL_NOT_FOUND", "Интеграция канала не найдена") }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun remove(id: ChannelIntegrationId) {
        tr.sql("DELETE FROM channel_integrations WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    context(tr: Transaction)
    override suspend fun senderById(id: ChannelIntegrationId): ChannelIntegration? =
        tr.sql("SELECT id, name, config::text AS config, enabled FROM channel_integrations WHERE id = :id")
            .bind("id", id)
            .firstOrNull { row -> row.toChannelIntegration(factory) }
}

/** Собирает интеграцию-отправитель из строки результата через [factory]. */
internal fun Row.toChannelIntegration(factory: ChannelIntegrationFactory): ChannelIntegration =
    factory.create(
        id = asUuid("id").toChannelIntegrationId(),
        name = asString("name"),
        config = decodeChannelConfig(asString("config")),
        enabled = asBoolean("enabled"),
    )

/** Сериализует конфиг канала для хранения в jsonb-колонке. */
internal fun encodeChannelConfig(config: ChannelConfig): String = Json.encodeToString(ChannelConfig.serializer(), config)

/** Десериализует конфиг канала из текстового представления jsonb-колонки. */
internal fun decodeChannelConfig(text: String): ChannelConfig = Json.decodeFromString(ChannelConfig.serializer(), text)
