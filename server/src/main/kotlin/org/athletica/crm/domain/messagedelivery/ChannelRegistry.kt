package org.athletica.crm.domain.messagedelivery

import org.athletica.crm.core.messaging.ChannelConfig

/**
 * Реестр адаптеров каналов: по типизированному конфигу интеграции строит сконфигурированный
 * [MessageChannel] для отправки.
 *
 * Дискриминатор — конкретный подтип [ChannelConfig] (то есть провайдер), а не тип канала: это
 * позволяет иметь несколько провайдеров одного типа (например, несколько SMS-провайдеров).
 * Добавление реального провайдера — новая ветка здесь, не затрагивающая ни домен, ни диспетчер.
 */
interface ChannelRegistry {
    /** Возвращает адаптер канала, сконфигурированный значениями [config]. */
    fun resolve(config: ChannelConfig): MessageChannel
}
