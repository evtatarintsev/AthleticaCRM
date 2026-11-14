package org.athletica.crm.domain.messagedelivery

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.athletica.crm.core.errors.orThrow
import org.athletica.crm.domain.channels.ChannelIntegrations
import org.athletica.crm.domain.channels.SendResult
import org.athletica.crm.storage.Database
import org.slf4j.LoggerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Фоновый диспетчер исходящих доставок (outbox).
 *
 * Поллит доставки в статусе `PENDING` (только для включённых интеграций), отправляет их через
 * саму интеграцию ([org.athletica.crm.domain.channels.ChannelIntegration.send]) и проставляет
 * итоговый статус:
 * - успех -> `SENT` с идентификатором у провайдера;
 * - [SendResult.PermanentFailure] -> `FAILED`;
 * - [SendResult.TransientFailure] -> повтор (увеличивается счётчик попыток), а при достижении
 *   [maxRetries] -> `FAILED`.
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
        pending.forEach { delivery -> dispatch(delivery) }
    }

    private suspend fun dispatch(delivery: PendingDelivery) {
        val sender = db.transaction { orThrow { channels.senderById(delivery.channelIntegrationId) } } ?: return

        when (val result = sender.send(delivery.recipientAddress, delivery.body)) {
            is SendResult.Sent ->
                db.transaction { orThrow { deliveries.markSent(delivery.id, ProviderMessageRef(result.providerRef)) } }

            is SendResult.PermanentFailure ->
                db.transaction { orThrow { deliveries.markFailed(delivery.id, DeliveryError(result.code, result.message)) } }

            is SendResult.TransientFailure ->
                if (delivery.attempts + 1 >= maxRetries) {
                    db.transaction { orThrow { deliveries.markFailed(delivery.id, DeliveryError(result.code, result.message)) } }
                } else {
                    db.transaction { orThrow { deliveries.retryLater(delivery.id, DeliveryError(result.code, result.message)) } }
                }
        }
    }
}
