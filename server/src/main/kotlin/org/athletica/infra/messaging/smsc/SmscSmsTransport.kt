package org.athletica.infra.messaging.smsc

import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.SendResult
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Транспорт « {}", address, body:SMS через SMSC» с настройками [settings].
 * Первая итерация — заглушка: ничего реально не отправляет, только логирует.
 */
class SmscSmsTransport(
    private val settings: ChannelConfig.SmscSms,
) : ChannelTransport {
    private val logger = LoggerFactory.getLogger(SmscSmsTransport::class.java)

    override suspend fun send(address: String, body: String): SendResult {
        logger.info("STUB[smsc_sms login={}] -> {}: {}", settings.login, address, body)
        return SendResult.Sent("stub-${Uuid.generateV7()}")
    }
}
