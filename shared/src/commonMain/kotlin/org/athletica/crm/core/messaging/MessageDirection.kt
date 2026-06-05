package org.athletica.crm.core.messaging

/** Направление сообщения относительно организации. Дискриминатор подтипа сообщения в БД и схемах. */
enum class MessageDirection {
    OUTBOUND,
    INBOUND,
}
