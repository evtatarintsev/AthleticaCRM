package org.athletica.infra.messaging

import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelBindings
import org.athletica.crm.domain.channels.ChannelIntegration
import org.athletica.crm.domain.channels.ChannelIntegrationFactory
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.infra.messaging.smsc.SmscSmsChannel
import org.athletica.infra.messaging.telegram.TelegramBotChannel
import org.athletica.infra.messaging.twilio.TwilioSmsChannel

/**
 * Фабрика интеграций первой итерации: собирает реализацию канала по подтипу [ChannelConfig].
 * Прямые каналы получают [contacts] (адрес из контакта), связанные — [bindings] (адрес из связки).
 * Все реализации — заглушки (реально не отправляют). Добавление реального провайдера — новая ветка здесь.
 */
class StubChannelIntegrationFactory(
    private val contacts: ClientContacts,
    private val bindings: ChannelBindings,
) : ChannelIntegrationFactory {
    override fun create(
        id: ChannelIntegrationId,
        name: String,
        config: ChannelConfig,
        enabled: Boolean,
    ): ChannelIntegration =
        when (config) {
            is ChannelConfig.TwilioSms -> TwilioSmsChannel(id, name, config, enabled, contacts)
            is ChannelConfig.SmscSms -> SmscSmsChannel(id, name, config, enabled, contacts)
            is ChannelConfig.TelegramBot -> TelegramBotChannel(id, name, config, enabled, bindings)
        }
}
