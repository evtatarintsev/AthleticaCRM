package org.athletica.crm.read.messaging

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
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
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.core.messaging.toChannelConfig
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [ClientChannelsView] поверх PostgreSQL: один запрос — включённые каналы организации
 * с признаком связки клиента, соединённые с контактами клиента (строка на пару «канал × контакт»).
 * Какие контакты годятся каналу и доступен ли он, решает правило адресации вида канала
 * ([org.athletica.crm.core.messaging.ChannelAddressing]) — то же, что применяет отправка; в SQL оно
 * не повторяется, иначе разошлось бы с доменом.
 */
class DbClientChannelsView : ClientChannelsView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(clientId: ClientId): ClientChannelsResponse {
        val rows =
            tr
                .sql(
                    """
                    SELECT ci.id, ci.name, ci.config::text AS config,
                           EXISTS (
                               SELECT 1 FROM channel_bindings b
                               WHERE b.integration_id = ci.id AND b.client_id = :clientId AND b.org_id = :orgId
                           ) AS linked,
                           cc.id AS contact_id, cc.type AS contact_type, cc.value AS contact_value
                    FROM channel_integrations ci
                    LEFT JOIN client_contacts cc ON cc.org_id = :orgId AND cc.client_id = :clientId
                    WHERE ci.org_id = :orgId AND ci.enabled
                    ORDER BY ci.created_at, ci.id, cc.created_at
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("clientId", clientId)
                .list { row -> row.toChannelContactRow() }
        val channels =
            rows
                .groupBy { it.id }
                .values
                .map { channelRows -> channelRows.first().toSchema(channelRows.mapNotNull { it.contact }) }
        return ClientChannelsResponse(channels)
    }

    /** Строка выборки: канал и один из контактов клиента (`null`, если контактов нет). */
    private class ChannelContactRow(
        /** Идентификатор интеграции. */
        val id: ChannelIntegrationId,
        /** Отображаемое имя. */
        val name: String,
        /** Настройки вида канала: нужны только чтобы вычислить адресацию и провайдера. */
        val config: ChannelConfig,
        /** Есть ли у клиента связка с этим каналом. */
        val linked: Boolean,
        /** Контакт клиента из соединения. */
        val contact: ClientContactSchema?,
    ) {
        /** Схема канала для клиента с контактами [clientContacts]. */
        fun toSchema(clientContacts: List<ClientContactSchema>): ClientChannelSchema {
            val addressing = config.addressing
            val addressable = clientContacts.filter { addressing.accepts(it.type) }
            return ClientChannelSchema(
                id = id,
                name = name,
                provider = config.provider,
                available = addressing.reachable(hasAcceptedContact = addressable.isNotEmpty(), linked = linked),
                contacts = addressable,
            )
        }
    }

    /** Собирает строку «канал × контакт» из результата выборки. */
    private fun Row.toChannelContactRow(): ChannelContactRow =
        ChannelContactRow(
            id = asUuid("id").toChannelIntegrationId(),
            name = asString("name"),
            config = asString("config").toChannelConfig(),
            linked = asBoolean("linked"),
            contact =
                asUuidOrNull("contact_id")?.let { contactId ->
                    ClientContactSchema(
                        id = contactId.toClientContactId(),
                        type = ContactType.valueOf(asString("contact_type")),
                        value = asString("contact_value"),
                    )
                },
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
