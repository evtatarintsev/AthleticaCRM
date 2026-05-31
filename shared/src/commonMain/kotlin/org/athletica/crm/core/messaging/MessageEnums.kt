package org.athletica.crm.core.messaging

/**
 * Статус сообщения в outbox-конвейере.
 *
 * Жизненный цикл исходящего: [QUEUED] -> [SENDING] -> [SENT] -> [DELIVERED] -> [READ],
 * ветка ошибки — [FAILED]. Входящие сообщения сразу создаются в [DELIVERED].
 */
enum class MessageStatus {
    QUEUED,
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED,
}

/** Направление сообщения относительно организации. */
enum class MessageDirection {
    OUTBOUND,
    INBOUND,
}

/** Кто отправил сообщение: сотрудник, система (автоматика) или сам клиент. */
enum class SenderKind {
    ADMIN,
    SYSTEM,
    CLIENT,
}
