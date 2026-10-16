package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelConfig

/**
 * Проверка настроек канала у провайдера перед сохранением. Обращается к внешнему сервису,
 * поэтому вызывается до открытия транзакции. Реализация — в `infra.messaging`.
 */
interface ChannelConfigVerifier {
    /**
     * Проверяет [config] и возвращает его, дополненный данными провайдера (например, имя бота).
     * Ошибка провайдера — `Raise` с кодом, специфичным для вида канала, и текстом на языке [ctx].
     */
    context(ctx: RequestContext, raise: Raise<DomainError>)
    suspend fun verified(config: ChannelConfig): ChannelConfig
}
