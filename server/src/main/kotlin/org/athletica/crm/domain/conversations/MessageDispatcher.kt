package org.athletica.crm.domain.conversations

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.storage.Database
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Фоновый диспетчер исходящих сообщений (outbox).
 *
 * Поллит сообщения в статусе `QUEUED`, отправляет их через адаптер канала из [registry] и
 * проставляет итоговый статус:
 * - успех -> `SENT` с идентификатором у провайдера;
 * - [SendError.Permanent] -> `FAILED`;
 * - [SendError.Transient] -> повтор (увеличивается счётчик попыток), а при достижении
 *   [maxRetries] -> `FAILED`.
 *
 * Работает в одном процессе с приложением; запускается в отдельной корутине при старте.
 */
class MessageDispatcher(
    private val db: Database,
    private val messages: DbMessages,
    private val channels: ChannelIntegrations,
    private val registry: ChannelRegistry,
    private val checkEvery: Duration = 5.seconds,
    private val maxRetries: Int = 5,
) {
    private val logger = LoggerFactory.getLogger(MessageDispatcher::class.java)

    /** Запускает бесконечный цикл поллинга. Должен выполняться в отдельной корутине. */
    suspend fun dispatchPending() {
        logger.info("Диспетчер сообщений запущен")
        while (currentCoroutineContext().isActive) {
            try {
                pollOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Ошибка в цикле диспетчера сообщений: ${e.message}", e)
            }
            delay(checkEvery)
        }
    }

    /** Обрабатывает одну порцию сообщений из очереди. Выделено для прямого вызова в тестах. */
    suspend fun pollOnce() {
        val pending = db.transaction { context(this) { messages.pendingOutbound(20) } }
        pending.forEach { message -> dispatch(message) }
    }

    private suspend fun dispatch(message: PendingMessage) {
        val config =
            message.channelIntegrationId
                ?.let { id -> db.transaction { context(this) { channels.configById(id) } } }
                ?: emptyMap()
        val channel = registry.resolve(message.channelType, config)
        val outbound = OutboundMessage(message.recipientAddress, message.body, config)

        channel.send(outbound).fold(
            { error -> onError(message, error) },
            { ref -> db.transaction { context(this) { messages.markSent(message.id, ref) } } },
        )
    }

    private suspend fun onError(message: PendingMessage, error: SendError) {
        when (error) {
            is SendError.Permanent ->
                db.transaction { context(this) { messages.markFailed(message.id, error.code, error.message) } }

            is SendError.Transient ->
                if (message.retryCount + 1 >= maxRetries) {
                    db.transaction { context(this) { messages.markFailed(message.id, error.code, error.message) } }
                } else {
                    db.transaction { context(this) { messages.retryLater(message.id, error.message) } }
                }
        }
    }
}
