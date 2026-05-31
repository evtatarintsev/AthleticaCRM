package org.athletica.crm.domain.conversations

import org.athletica.crm.core.messaging.ChannelType

/**
 * Реестр адаптеров каналов: по типу канала и конфигу интеграции строит [MessageChannel] для отправки.
 *
 * На первой итерации реальные провайдеры отсутствуют — реализация ([StubChannelRegistry])
 * возвращает заглушку для любого типа. Добавление реального провайдера — это новая ветка здесь,
 * не затрагивающая ни домен, ни диспетчер.
 */
interface ChannelRegistry {
    /** Возвращает адаптер канала [channelType], сконфигурированный значениями [config]. */
    fun resolve(channelType: ChannelType, config: Map<String, String>): MessageChannel
}
