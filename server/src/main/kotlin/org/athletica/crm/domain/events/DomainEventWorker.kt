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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Воркер, который поллит таблицу [domain_events] и диспатчит необработанные события.
 *
 * Гарантии:
 * - At-least-once: при крэше после [DomainEventBus.dispatch] но до [markProcessed] событие повторится.
 * - После ошибки событие повторяется с нарастающей задержкой ([retryDelay]); после [MAX_ATTEMPTS]
 *   неудачных попыток помечается `failed_at` — не выполненным — и ждёт перезапуска администратором
 *   (`admin events retry-failed`).
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

    /** Обрабатывает одну пачку событий, чья очередная попытка наступила. */
    suspend fun processBatch() {
        val records = db.transaction { fetchPending() }
        records.forEach { record ->
            try {
                bus.dispatch(record)
                db.transaction { markProcessed(record.id) }
            } catch (e: Exception) {
                logger.error("Ошибка обработки события ${record.id} (${record.eventType}): ${e.message}")
                db.transaction { registerFailure(record, e.message ?: "unknown error") }
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
                WHERE processed_at IS NULL AND failed_at IS NULL AND next_attempt_at <= now()
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

    /**
     * Учитывает неудачную попытку обработки [record] с ошибкой [error]: назначает следующую попытку
     * через [retryDelay] или, если попыток стало [MAX_ATTEMPTS], помечает событие неуспешным.
     */
    context(tr: Transaction)
    private suspend fun registerFailure(
        record: DomainEventRecord,
        error: String,
    ) {
        val attempts = record.retryCount + 1
        if (attempts >= MAX_ATTEMPTS) {
            logger.error("Событие ${record.id} (${record.eventType}) исчерпало $MAX_ATTEMPTS попыток и помечено неуспешным")
        }
        tr
            .sql(
                """
                UPDATE domain_events
                SET retry_count = :attempts,
                    last_error = :error,
                    next_attempt_at = now() + make_interval(secs => :delaySeconds),
                    failed_at = CASE WHEN :attempts >= :maxAttempts THEN now() END
                WHERE id = :id
                """.trimIndent(),
            )
            .bind("id", record.id)
            .bind("attempts", attempts)
            .bind("error", error)
            .bind("delaySeconds", retryDelay(record.retryCount).inWholeSeconds.toDouble())
            .bind("maxAttempts", MAX_ATTEMPTS)
            .execute()
    }

    companion object {
        /** Сколько попыток обработки даётся событию до пометки неуспешным. */
        const val MAX_ATTEMPTS = 12

        /** Задержка перед первой повторной попыткой. */
        private val FIRST_RETRY_DELAY = 10.seconds

        /** Наибольшая задержка между попытками. */
        private val MAX_RETRY_DELAY = 1.hours

        /**
         * Задержка перед следующей попыткой после [failedAttempts] уже неудачных:
         * 10 с · 2^failedAttempts, но не больше часа. За [MAX_ATTEMPTS] попыток — около 10 часов.
         */
        fun retryDelay(failedAttempts: Int): Duration =
            if (failedAttempts >= 20) {
                MAX_RETRY_DELAY
            } else {
                minOf(FIRST_RETRY_DELAY * (1 shl failedAttempts), MAX_RETRY_DELAY)
            }
    }
}
