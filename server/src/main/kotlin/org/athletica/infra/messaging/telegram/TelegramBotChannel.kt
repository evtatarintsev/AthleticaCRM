package org.athletica.infra.messaging.telegram

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.channels.ChannelBindings
import org.athletica.crm.domain.channels.ChannelIntegration
import org.athletica.crm.domain.channels.SendResult
import org.athletica.crm.storage.Transaction
import org.athletica.infra.messaging.bindingAddress
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Telegram-канал через бота (связанный: адрес — chat_id из связки, а не контакт).
 * Первая итерация — заглушка: ничего реально не отправляет, только логирует.
 */
class TelegramBotChannel(
    override val id: ChannelIntegrationId,
    override val name: String,
    private val settings: ChannelConfig.TelegramBot,
    override val enabled: Boolean,
    private val bindings: ChannelBindings,
) : ChannelIntegration {
    private val logger = LoggerFactory.getLogger(TelegramBotChannel::class.java)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun recipientAddress(clientId: ClientId, contactId: ClientContactId?): String = bindingAddress(id, clientId, bindings)

    override suspend fun send(address: String, body: String): SendResult {
        logger.info("STUB[telegram_bot] -> {}: {}", address, body)
        return SendResult.Sent("stub-${Uuid.generateV7()}")
    }
}
