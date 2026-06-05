package org.athletica.crm.core.messaging

/**
 * Конкретный провайдер канала связи.
 *
 * Один [ChannelType] может обслуживаться несколькими провайдерами (например, SMS — Twilio, SMSC, …).
 * Поэтому диспетчеризация отправки и реестр адаптеров опираются на провайдера, а не на тип канала.
 * [channelType] выводит тип канала из провайдера: провайдер всегда принадлежит ровно одному типу.
 */
enum class ChannelProvider(
    /** Тип канала, к которому относится провайдер. */
    val channelType: ChannelType,
) {
    TWILIO_SMS(ChannelType.SMS),
    SMSC_SMS(ChannelType.SMS),
    TELEGRAM_BOT(ChannelType.TELEGRAM),
    IN_APP(ChannelType.IN_APP),
}
