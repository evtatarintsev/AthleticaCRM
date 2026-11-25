package org.athletica.crm.domain.clientapp.codes

import arrow.core.raise.context.Raise
import org.athletica.crm.domain.clientcontacts.PhoneDigits
import org.slf4j.LoggerFactory

/**
 * Заглушка отправителя для локальной разработки: пишет код в лог и отвечает успехом.
 * Подключается в `Di` только в режиме разработки Ktor: в проде код в логе недопустим.
 */
class LoggingVerificationCodeSender(
    override val channel: CodeChannel,
) : VerificationCodeSender {
    private val logger = LoggerFactory.getLogger(LoggingVerificationCodeSender::class.java)

    context(raise: Raise<CodeDeliveryError>)
    override suspend fun send(
        phone: PhoneDigits,
        code: VerificationCode,
    ) {
        logger.info("Код входа в кабинет для {} через {}: {}", phone.value, channel, code.value)
    }
}
