package org.athletica.crm.domain.messagedelivery

import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.DeliveryId
import org.athletica.crm.core.entityids.MessageId
import kotlin.time.Instant

/**
 * Доставка сообщения в конкретный канал.
 *
 * Одно сообщение может иметь несколько доставок (фан-аут по каналам). Доставка — носитель статуса
 * отправки, отделённый от контента сообщения. [recipientAddress] — адрес в адресном пространстве
 * канала (телефон/chat_id/email; для IN_APP — идентификатор клиента строкой), он есть всегда.
 */
data class Delivery(
    val id: DeliveryId,
    val messageId: MessageId,
    val channelIntegrationId: ChannelIntegrationId,
    val recipientAddress: String,
    val state: DeliveryState,
    val providerRef: ProviderMessageRef?,
    val attempts: Int,
    val createdAt: Instant,
)

/**
 * Состояние доставки. Сумма-тип: ошибка ([DeliveryError]) представима только в [Failed],
 * поэтому «успешная доставка с текстом ошибки» невозможна. [Pending] — доменный термин «ещё не
 * отправлено», не зависящий от наличия очереди.
 */
sealed interface DeliveryState {
    /** Поставлена, ещё не отправлена провайдеру. */
    data object Pending : DeliveryState

    /** Отправлена провайдеру. */
    data object Sent : DeliveryState

    /** Подтверждена доставка провайдером. */
    data object Delivered : DeliveryState

    /** Терминальный отказ доставки. */
    data class Failed(val error: DeliveryError) : DeliveryState
}

/** Терминальная ошибка доставки (persisted). */
data class DeliveryError(val code: String, val message: String)

/** Идентификатор отправленного сообщения на стороне провайдера (для матчинга квитанций о доставке). */
data class ProviderMessageRef(val value: String)
