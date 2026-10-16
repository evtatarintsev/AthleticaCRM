package org.athletica.infra.messaging.telegram

import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelTransport
import org.athletica.crm.domain.channels.SendResult
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Транспорт «Telegram-бот» с настройками [settings].
 * Первая итерация — заглушка: ничего реально не отправляет, только логирует.
 */
class TelegramBotTransport(
    private val settings: ChannelConfig.TelegramBot,
) : ChannelTransport {
    private val logger = LoggerFactory.getLogger(TelegramBotTransport::class.java)

    override suspend fun send(address: String, body: String): SendResult {
        logger.info("STUB[telegram_bot] -> {}: {}", address, body)
        return SendResult.Sent("stub-${Uuid.generateV7()}")
    }
}
