package org.athletica.infra.messaging

import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.ChannelTransports
import org.athletica.infra.messaging.smsc.SmscSmsTransport
import org.athletica.infra.messaging.telegram.TelegramBotTransport
import org.athletica.infra.messaging.twilio.TwilioSmsTransport

/**
 * Транспорты провайдеров первой итерации: все — заглушки (реально не отправляют).
 * Подключение реального провайдера — замена соответствующей ветки здесь.
 */
class StubChannelTransports : ChannelTransports {
    override fun transport(config: ChannelConfig): ChannelTransport =
        when (config) {
            is ChannelConfig.TwilioSms -> TwilioSmsTransport(config)
            is ChannelConfig.SmscSms -> SmscSmsTransport(config)
            is ChannelConfig.TelegramBot -> TelegramBotTransport(config)
        }
}
