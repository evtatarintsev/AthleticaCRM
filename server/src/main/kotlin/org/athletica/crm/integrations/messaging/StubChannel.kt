package org.athletica.crm.integrations.messaging

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.domain.conversations.ChannelRegistry
import org.athletica.crm.domain.conversations.MessageChannel
import org.athletica.crm.domain.conversations.OutboundMessage
import org.athletica.crm.domain.conversations.ProviderMessageRef
import org.athletica.crm.domain.conversations.SendError
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Заглушка-адаптер канала: ничего реально не отправляет, только логирует и возвращает успех.
 * Нужна, чтобы прогнать весь outbox-конвейер end-to-end без внешних зависимостей и токенов.
 */
class StubChannel(override val type: ChannelType) : MessageChannel {
    private val logger = LoggerFactory.getLogger(StubChannel::class.java)

    override suspend fun send(message: OutboundMessage): Either<SendError, ProviderMessageRef> {
        logger.info("STUB[$type] -> {}: {}", message.recipientAddress ?: "in-app", message.body)
        return ProviderMessageRef("stub-${Uuid.generateV7()}").right()
    }
}

/** Реестр, возвращающий [StubChannel] для любого типа канала. Первая итерация без реальных провайдеров. */
class StubChannelRegistry : ChannelRegistry {
    override fun resolve(channelType: ChannelType, config: Map<String, String>): MessageChannel = StubChannel(channelType)
}
