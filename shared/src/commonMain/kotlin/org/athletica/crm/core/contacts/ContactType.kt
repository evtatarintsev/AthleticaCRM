package org.athletica.crm.core.contacts

import org.athletica.crm.core.messaging.ChannelType

/**
 * Тип контакта клиента: чем является значение контакта (номер телефона, email, имя пользователя
 * и т.п.). В отличие от [ChannelType] (способ доставки), тип описывает сам адрес.
 *
 * Каналы, по которым контакт можно использовать, выводятся из типа через [compatibleChannels]
 * и не хранятся отдельно: один телефон годится сразу для нескольких каналов.
 */
enum class ContactType(
    /** Каналы доставки, для которых пригоден контакт данного типа. */
    val compatibleChannels: Set<ChannelType>,
) {
    /** Номер телефона: подходит для SMS, WhatsApp, Telegram и MAX. */
    PHONE(setOf(ChannelType.SMS, ChannelType.WHATSAPP, ChannelType.TELEGRAM, ChannelType.MAX)),

    /** Адрес электронной почты. */
    EMAIL(setOf(ChannelType.EMAIL)),

    /** Имя пользователя в Telegram. */
    TELEGRAM(setOf(ChannelType.TELEGRAM)),

    /** Профиль во ВКонтакте. */
    VK(setOf(ChannelType.VK)),

    /** Профиль в Facebook: пока без канала доставки, хранится как справочный. */
    FACEBOOK(emptySet()),
}
