package org.athletica.crm.core.messaging

import org.athletica.crm.core.contacts.ContactType

/**
 * Способ, которым вид канала находит адрес получателя-клиента.
 * Свойство вида канала ([ChannelConfig.addressing]), а не конкретного провайдера:
 * два SMS-провайдера адресуются одинаково — по телефону.
 *
 * Единственное место правила адресации: им пользуются и отправка (домен), и проекция доступности
 * каналов для страницы переписки.
 */
sealed interface ChannelAddressing {
    /** Годится ли контакт типа [type] адресом получателя в этом канале. */
    fun accepts(type: ContactType): Boolean

    /**
     * Можно ли написать клиенту через канал: [hasAcceptedContact] — у клиента есть контакт, который
     * канал [accepts]; [linked] — клиент связан с каналом.
     */
    fun reachable(hasAcceptedContact: Boolean, linked: Boolean): Boolean

    /**
     * Прямой канал: адрес — один из контактов клиента типа из [contactTypes]
     * (телефон для SMS, email для почты). Сотрудник может выбрать конкретный контакт.
     */
    data class ByContact(
        /** Типы контактов, которые канал умеет адресовать. */
        val contactTypes: Set<ContactType>,
    ) : ChannelAddressing {
        override fun accepts(type: ContactType): Boolean = type in contactTypes

        override fun reachable(hasAcceptedContact: Boolean, linked: Boolean): Boolean = hasAcceptedContact
    }

    /**
     * Связанный канал: адрес (chat_id, vk_id) появляется, только когда клиент сам написал в канал,
     * и хранится в связке «адрес ↔ клиент». Контакт из карточки клиента для отправки не годится:
     * по имени пользователя Telegram-бот написать не может.
     */
    data object ByBinding : ChannelAddressing {
        override fun accepts(type: ContactType): Boolean = false

        override fun reachable(hasAcceptedContact: Boolean, linked: Boolean): Boolean = linked
    }
}
