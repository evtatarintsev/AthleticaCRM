package org.athletica.crm.domain.events

import org.athletica.crm.storage.Database
import kotlin.uuid.Uuid

/**
 * Неуспешные доменные события — исчерпавшие попытки обработки ([DomainEventWorker.MAX_ATTEMPTS]).
 * Операции идут поверх всех организаций и используются административными командами.
 */
class FailedDomainEvents(private val database: Database) {
    /**
     * Возвращает неуспешные события в обработку: сбрасывает `failed_at`, счётчик попыток и время
     * следующей попытки. [orgId] ограничивает организацией, [eventId] — одним событием; `null` — без
     * ограничения. Возвращает число перезапущенных событий.
     */
    suspend fun retry(orgId: Uuid?, eventId: Uuid?): Long =
        database.transaction {
            sql(
                """
                UPDATE domain_events
                SET failed_at = NULL, retry_count = 0, next_attempt_at = now()
                WHERE failed_at IS NOT NULL
                  AND (CAST(:orgId AS uuid) IS NULL OR org_id = :orgId)
                  AND (CAST(:eventId AS uuid) IS NULL OR id = :eventId)
                """.trimIndent(),
            )
                .bind("orgId", orgId)
                .bind("eventId", eventId)
                .execute()
        }
}
