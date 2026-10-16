package org.athletica.infra.messaging

import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.ChannelTransports
import org.athletica.infra.messaging.smsc.SmscSmsTransport
import org.athletica.infra.messaging.telegram.TelegramBotApi
import org.athletica.infra.messaging.telegram.TelegramBotTransport
import org.athletica.infra.messaging.twilio.TwilioSmsTransport

/**
 * Транспорты провайдеров: Telegram отправляет через Bot API [telegram], SMS-провайдеры пока заглушки.
 * Подключение реального провайдера — замена соответствующей ветки здесь.
 */
class ProviderChannelTransports(
    private val telegram: TelegramBotApi,
) : ChannelTransports {
    override fun transport(config: ChannelConfig): ChannelTransport =
        when (config) {
            is ChannelConfig.TwilioSms -> TwilioSmsTransport(config)
            is ChannelConfig.SmscSms -> SmscSmsTransport(config)
            is ChannelConfig.TelegramBot -> TelegramBotTransport(config, telegram)
        }
}
