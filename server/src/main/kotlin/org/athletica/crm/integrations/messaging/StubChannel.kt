package org.athletica.crm.integrations.messaging

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.core.messaging.ChannelConfig
import org.athletica.crm.domain.messagedelivery.ChannelRegistry
import org.athletica.crm.domain.messagedelivery.ChannelSendRequest
import org.athletica.crm.domain.messagedelivery.MessageChannel
import org.athletica.crm.domain.messagedelivery.ProviderMessageRef
import org.athletica.crm.domain.messagedelivery.SendError
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Заглушка-адаптер канала: ничего реально не отправляет, только логирует и возвращает успех.
 * Нужна, чтобы прогнать весь outbox-конвейер end-to-end без внешних зависимостей и токенов.
 */
class StubChannel(private val provider: String) : MessageChannel {
    private val logger = LoggerFactory.getLogger(StubChannel::class.java)

    override suspend fun send(request: ChannelSendRequest): Either<SendError, ProviderMessageRef> {
        logger.info("STUB[$provider] -> {}: {}", request.recipientAddress, request.body)
        return ProviderMessageRef("stub-${Uuid.generateV7()}").right()
    }
}

/** Реестр, возвращающий [StubChannel] для любого конфига. Первая итерация без реальных провайдеров. */
class StubChannelRegistry : ChannelRegistry {
    override fun resolve(config: ChannelConfig): MessageChannel = StubChannel(config.provider.name)
}
