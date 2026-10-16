package org.athletica.infra.messaging.twilio

import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.SendResult
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Транспорт «SMS через Twilio» с настройками [settings].
 * Первая итерация — заглушка: ничего реально не отправляет, только логирует.
 */
class TwilioSmsTransport(
    private val settings: ChannelConfig.TwilioSms,
) : ChannelTransport {
    private val logger = LoggerFactory.getLogger(TwilioSmsTransport::class.java)

    override suspend fun send(address: String, body: String): SendResult {
        logger.info("STUB[twilio_sms from={}] -> {}: {}", settings.from, address, body)
        return SendResult.Sent("stub-${Uuid.generateV7()}")
    }
}
