package org.athletica.crm.domain.events

import kotlinx.coroutines.delay
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Воркер, который поллит таблицу [domain_events] и диспатчит необработанные события.
 *
 * Гарантии:
 * - At-least-once: при крэше после [DomainEventBus.dispatch] но до [markProcessed] событие повторится.
 * - При [retryCount] >= 5 пишет ошибку в лог и пропускает событие навсегда (dead letter).
 * - Обработчики вызываются вне любой транзакции и открывают свои транзакции при необходимости.
 *
 * [pollingInterval] — интервал между итерациями при отсутствии событий.
 */
class DomainEventWorker(
    private val db: Database,
    private val bus: DomainEventBus,
    private val pollingInterval: Duration = 1.seconds,
) {
    private val logger = LoggerFactory.getLogger(DomainEventWorker::class.java)

    /** Запускает бесконечный цикл поллинга. Должен выполняться в отдельной корутине. */
    suspend fun run() {
        logger.info("Воркер доменных событий запущен")
        while (true) {
            try {
                processBatch()
            } catch (e: Exception) {
                logger.error("Ошибка в цикле обработки событий: ${e.message}", e)
            }
            delay(pollingInterval)
        }
    }

    private suspend fun processBatch() {
        val records = db.transaction { fetchPending() }
        records.forEach { record ->
            if (record.retryCount >= 5) {
                logger.error("Событие ${record.id} (${record.eventType}) превысило лимит попыток, пропускаем")
                db.transaction { markProcessed(record.id) }
                return@forEach
            }
            try {
                bus.dispatch(record)
                db.transaction { markProcessed(record.id) }
            } catch (e: Exception) {
                logger.error("Ошибка обработки события ${record.id} (${record.eventType}): ${e.message}")
                db.transaction { incrementRetry(record.id, e.message ?: "unknown error") }
            }
        }
    }

    context(tr: Transaction)
    private suspend fun fetchPending(batchSize: Int = 10): List<DomainEventRecord> =
        tr
            .sql(
                """
                SELECT id, org_id, event_type, payload::text, retry_count
                FROM domain_events
                WHERE processed_at IS NULL AND retry_count < 5
                ORDER BY created_at
                LIMIT :batchSize
                FOR UPDATE SKIP LOCKED
                """.trimIndent(),
            )
            .bind("batchSize", batchSize)
            .list { row ->
                DomainEventRecord(
                    id = row.asUuid("id"),
                    orgId = OrgId(row.asUuid("org_id")),
                    eventType = row.asString("event_type"),
                    payload = row.asString("payload"),
                    retryCount = row.asInt("retry_count"),
                )
            }

    context(tr: Transaction)
    private suspend fun markProcessed(id: Uuid) {
        tr
            .sql("UPDATE domain_events SET processed_at = now() WHERE id = :id")
            .bind("id", id)
            .execute()
    }

    context(tr: Transaction)
    private suspend fun incrementRetry(
        id: Uuid,
        error: String,
    ) {
        tr
            .sql("UPDATE domain_events SET retry_count = retry_count + 1, last_error = :error WHERE id = :id")
            .bind("id", id)
            .bind("error", error)
            .execute()
    }
}
