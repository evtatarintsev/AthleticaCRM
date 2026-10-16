package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.toChannelConfig
import org.athletica.crm.core.messaging.toJson
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * Реализация коллекции интеграций каналов на PostgreSQL через R2DBC.
 * Строка результата собирается в [DbChannelIntegration]: транспорт провайдера берётся из [transports],
 * адрес получателя разрешается через [contacts] (прямые каналы) или [bindings] (связанные).
 */
class DbChannelIntegrations(
    private val transports: ChannelTransports,
    private val contacts: ClientContacts,
    private val bindings: ChannelBindings,
) : ChannelIntegrations {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: ChannelIntegrationId): ChannelIntegration =
        tr.sql(
            """
            SELECT id, name, config::text AS config, enabled
            FROM channel_integrations
            WHERE id = :id AND org_id = :orgId
            FOR KEY SHARE
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toChannelIntegration() }
            ?: raise(CommonDomainError("CHANNEL_NOT_FOUND", "Интеграция канала не найдена"))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun add(id: ChannelIntegrationId, name: String, config: ChannelConfig) {
        ensureBotNotInUse(id, config)
        uniqueBot {
            tr.sql(
                """
                INSERT INTO channel_integrations (id, org_id, name, config, enabled)
                VALUES (:id, :orgId, :name, :config::jsonb, true)
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("name", name)
                .bind("config", config.toJson())
                .execute()
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(
        id: ChannelIntegrationId,
        name: String,
        config: ChannelConfig,
        enabled: Boolean,
    ) {
        ensureBotNotInUse(id, config)
        val updated =
            uniqueBot {
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
                    .bind("config", config.toJson())
                    .bind("enabled", enabled)
                    .execute()
            }
        ensure(updated != 0L) { CommonDomainError("CHANNEL_NOT_FOUND", "Интеграция канала не найдена") }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun remove(id: ChannelIntegrationId) {
        tr.sql("SELECT id FROM channel_integrations WHERE id = :id AND org_id = :orgId FOR UPDATE")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.asUuid("id") }
        val used =
            tr.sql(
                """
                SELECT EXISTS (
                    SELECT 1 FROM message_deliveries WHERE channel_integration_id = :id AND org_id = :orgId
                ) OR EXISTS (
                    SELECT 1 FROM inbound_receipts WHERE integration_id = :id AND org_id = :orgId
                ) AS used
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row -> row.asBoolean("used") } ?: false
        ensure(!used) {
            CommonDomainError("CHANNEL_IN_USE", "Через канал уже шли сообщения — отключите его вместо удаления")
        }
        tr.sql("DELETE FROM channel_integrations WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    context(tr: Transaction)
    override suspend fun senderById(id: ChannelIntegrationId): ChannelIntegration? =
        tr.sql("SELECT id, name, config::text AS config, enabled FROM channel_integrations WHERE id = :id")
            .bind("id", id)
            .firstOrNull { row -> row.toChannelIntegration() }

    /**
     * `TELEGRAM_BOT_IN_USE`, если бот из [config] уже подключён к другой интеграции (любой организации):
     * один бот опрашивается только одной интеграцией.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun ensureBotNotInUse(id: ChannelIntegrationId, config: ChannelConfig) {
        if (config !is ChannelConfig.TelegramBot) {
            return
        }
        val inUse =
            tr.sql(
                """
                SELECT EXISTS (
                    SELECT 1 FROM channel_integrations
                    WHERE config ->> 'type' = 'telegram_bot' AND config ->> 'botToken' = :token AND id <> :id
                ) AS in_use
                """.trimIndent(),
            )
                .bind("token", config.botToken)
                .bind("id", id)
                .firstOrNull { row -> row.asBoolean("in_use") } ?: false
        ensure(!inUse) { botInUse() }
    }

    /**
     * Выполняет запись [block], переводя нарушение уникального индекса токена бота в `TELEGRAM_BOT_IN_USE`:
     * индекс страхует гонку двух одновременных сохранений, которую не ловит [ensureBotNotInUse].
     */
    context(ctx: RequestContext, raise: Raise<DomainError>)
    private suspend fun uniqueBot(block: suspend () -> Long): Long =
        try {
            block()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(botInUse())
        }

    /** Ошибка «бот уже подключён к другой интеграции». */
    context(ctx: RequestContext)
    private fun botInUse(): CommonDomainError = CommonDomainError("TELEGRAM_BOT_IN_USE", Messages.TelegramBotInUse.localize())

    /** Собирает интеграцию-отправитель из строки результата. */
    private fun Row.toChannelIntegration(): ChannelIntegration {
        val config = asString("config").toChannelConfig()
        return DbChannelIntegration(
            id = asUuid("id").toChannelIntegrationId(),
            name = asString("name"),
            enabled = asBoolean("enabled"),
            addressing = config.addressing,
            transport = transports.transport(config),
            contacts = contacts,
            bindings = bindings,
        )
    }
}
