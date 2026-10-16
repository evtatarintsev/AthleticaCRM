package org.athletica.crm.domain.channels

import org.athletica.crm.core.messaging.ChannelConfig

/**
 * Транспорт провайдера: доставляет текст на уже разрешённый адрес. Ничего не знает о клиентах,
 * контактах и связках — только о протоколе провайдера. Реализации живут в `infra.messaging`.
 */
interface ChannelTransport {
    /** Отправляет текст [body] на адрес [address] в адресном пространстве канала. */
    suspend fun send(address: String, body: String): SendResult
}

/**
 * Источник транспортов: сопоставляет вид канала (подтип [ChannelConfig]) с реализацией провайдера.
 * Единственное место, где перечисляются провайдеры; реализация — в `infra.messaging`.
 */
interface ChannelTransports {
    /** Транспорт для настроек [config]. */
    fun transport(config: ChannelConfig): ChannelTransport
}
