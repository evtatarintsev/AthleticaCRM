package org.athletica.crm.domain.conversations

import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ConversationId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.MessageId
import kotlin.time.Instant

/**
 * Сообщение в диалоге — канал-агностичный контент. Полиморфно по направлению: для исходящего и
 * входящего значимы разные поля, поэтому это сумма-тип, а не один data class с nullable-полями.
 *
 * Механика отправки (адрес получателя, статусы доставки, ошибки) живёт в агрегате доставки
 * (`domain/messagedelivery`), а не здесь.
 */
sealed interface Message {
    val id: MessageId
    val conversationId: ConversationId
    val body: String
    val createdAt: Instant
}

/** Исходящее сообщение: автор — сотрудник или система, никогда не клиент. */
data class OutboundMessage(
    override val id: MessageId,
    override val conversationId: ConversationId,
    override val body: String,
    override val createdAt: Instant,
    val author: Author,
) : Message

/** Входящее сообщение: пришло от клиента беседы по каналу [receivedVia]. */
data class InboundMessage(
    override val id: MessageId,
    override val conversationId: ConversationId,
    override val body: String,
    override val createdAt: Instant,
    val receivedVia: ChannelIntegrationId,
) : Message

/** Автор исходящего сообщения. */
sealed interface Author {
    /** Конкретный сотрудник. */
    data class Employee(val id: EmployeeId) : Author

    /** Система (автоматическая рассылка/уведомление). */
    data object System : Author
}
