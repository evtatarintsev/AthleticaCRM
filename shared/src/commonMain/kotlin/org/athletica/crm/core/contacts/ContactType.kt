package org.athletica.crm.core.contacts

/**
 * Тип контакта клиента: чем является значение контакта (номер телефона, email, имя пользователя
 * и т.п.). Таксономия самого адреса; какие каналы умеют его адресовать — свойство вида канала
 * ([org.athletica.crm.core.messaging.ChannelConfig.addressing]), а не контакта.
 */
enum class ContactType {
    /** Номер телефона. */
    PHONE,

    /** Адрес электронной почты. */
    EMAIL,

    /** Имя пользователя в Telegram. */
    TELEGRAM,

    /** Профиль во ВКонтакте. */
    VK,

    /** Профиль в Facebook. */
    FACEBOOK,
}
