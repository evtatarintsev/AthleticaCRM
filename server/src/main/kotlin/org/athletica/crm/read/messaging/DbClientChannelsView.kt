package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.clients.ClientContactSchema
import org.athletica.crm.api.schemas.messaging.ChannelProviderSchema
import org.athletica.crm.api.schemas.messaging.ClientChannelSchema
import org.athletica.crm.api.schemas.messaging.ClientChannelsResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.entityids.toClientContactId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelAddressing
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * Реализация [ClientChannelsView] поверх PostgreSQL: один запрос за каналами (с признаком связки
 * клиента с каналом) и один — за контактами клиента. Доступность вычисляется по
 * [ChannelConfig.addressing]: прямому каналу нужен контакт подходящего типа, связанному — связка.
 */
class DbClientChannelsView : ClientChannelsView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(clientId: ClientId): ClientChannelsResponse {
        val contacts = contacts(clientId)
        val channels =
            tr
                .sql(
                    """
                    SELECT ci.id, ci.name, ci.config::text AS config,
                           EXISTS (
                               SELECT 1 FROM channel_bindings b
                               WHERE b.integration_id = ci.id AND b.client_id = :clientId AND b.org_id = :orgId
                           ) AS linked
                    FROM channel_integrations ci
                    WHERE ci.org_id = :orgId AND ci.enabled
                    ORDER BY ci.created_at
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("clientId", clientId)
                .list { row -> row.toChannelRow() }
        return ClientChannelsResponse(channels.map { it.toSchema(contacts) })
    }

    /** Контакты клиента [clientId] в порядке добавления. */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    private suspend fun contacts(clientId: ClientId): List<ClientContactSchema> =
        tr
            .sql(
                """
                SELECT id, type, value
                FROM client_contacts
                WHERE org_id = :orgId AND client_id = :clientId
                ORDER BY created_at
                """.trimIndent(),
            )
            .bind("orgId", ctx.orgId)
            .bind("clientId", clientId)
            .list { row ->
                ClientContactSchema(
                    id = row.asUuid("id").toClientContactId(),
                    type = ContactType.valueOf(row.asString("type")),
                    value = row.asString("value"),
                )
            }

    /** Строка выборки канала: настройки нужны только чтобы вычислить адресацию и провайдера. */
    private class ChannelRow(
        /** Идентификатор интеграции. */
        val id: ChannelIntegrationId,
        /** Отображаемое имя. */
        val name: String,
        /** Настройки вида канала. */
        val config: ChannelConfig,
        /** Есть ли у клиента связка с этим каналом. */
        val linked: Boolean,
    ) {
        /** Схема канала для клиента с контактами [clientContacts]. */
        fun toSchema(clientContacts: List<ClientContactSchema>): ClientChannelSchema {
            val addressable =
                when (val addressing = config.addressing) {
                    is ChannelAddressing.ByContact -> clientContacts.filter { it.type in addressing.contactTypes }
                    ChannelAddressing.ByBinding -> emptyList()
                }
            val available =
                when (config.addressing) {
                    is ChannelAddressing.ByContact -> addressable.isNotEmpty()
                    ChannelAddressing.ByBinding -> linked
                }
            return ClientChannelSchema(
                id = id,
                name = name,
                provider = config.provider,
                available = available,
                contacts = addressable,
            )
        }
    }

    /** Собирает строку канала из результата выборки. */
    private fun Row.toChannelRow(): ChannelRow =
        ChannelRow(
            id = asUuid("id").toChannelIntegrationId(),
            name = asString("name"),
            config = Json.decodeFromString(ChannelConfig.serializer(), asString("config")),
            linked = asBoolean("linked"),
        )

    private companion object {
        /** Провайдер канала для подписи в интерфейсе. */
        val ChannelConfig.provider: ChannelProviderSchema
            get() =
                when (this) {
                    is ChannelConfig.SmscSms -> ChannelProviderSchema.SMSC_SMS
                    is ChannelConfig.TelegramBot -> ChannelProviderSchema.TELEGRAM_BOT
                    is ChannelConfig.TwilioSms -> ChannelProviderSchema.TWILIO_SMS
                }
    }
}
