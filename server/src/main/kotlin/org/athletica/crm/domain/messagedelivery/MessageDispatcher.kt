package org.athletica.crm.domain.messagedelivery

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.athletica.crm.core.entityids.DeliveryId
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.storage.Database
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Фоновый диспетчер исходящих доставок (outbox).
 *
 * Поллит доставки в статусе `PENDING` (только для включённых интеграций), отправляет их через
 * саму интеграцию ([org.athletica.crm.domain.channels.ChannelIntegration.send]) и исполняет переход,
 * который выбирает [PendingDelivery.next] ([maxRetries] — предел попыток при временных ошибках).
 * Удалённая интеграция -> `FAILED` с кодом [PendingDelivery.CHANNEL_NOT_FOUND].
 *
 * Работает в одном процессе с приложением; запускается в отдельной корутине при старте.
 */
class MessageDispatcher(
    private val db: Database,
    private val deliveries: Deliveries,
    private val channels: ChannelIntegrations,
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

    /** Обрабатывает одну порцию доставок из очереди. Выделено для прямого вызова в тестах. */
    suspend fun pollOnce() {
        val pending = db.transaction { orThrow { deliveries.pending(20) } }
        pending.forEach { id -> dispatch(id) }
    }

    /**
     * Захватывает доставку [id], отправляет и фиксирует переход в одной транзакции: блокировка строки
     * не даёт другому обработчику отправить её повторно.
     */
    private suspend fun dispatch(id: DeliveryId) {
        db.transaction {
            orThrow {
                val delivery = deliveries.claim(id) ?: return@orThrow
                val sender = channels.senderById(delivery.channelIntegrationId)
                val transition =
                    if (sender == null) {
                        delivery.withoutSender()
                    } else {
                        delivery.next(sender.send(delivery.recipientAddress, delivery.body), maxRetries)
                    }
                when (transition) {
                    is DeliveryTransition.MarkSent -> deliveries.markSent(id, transition.ref)
                    is DeliveryTransition.Retry -> deliveries.retryLater(id, transition.error)
                    is DeliveryTransition.Fail -> deliveries.markFailed(id, transition.error)
                }
            }
        }
    }
}
