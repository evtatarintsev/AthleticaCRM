package org.athletica.crm.domain.audit

/**
 * Интерфейс асинхронного журнала аудита.
 *
 * Основная реализация — [PostgresAuditLog], которая пишет события в PostgreSQL
 * через буферизованный [kotlinx.coroutines.channels.Channel].
 * В тестах можно подставить stub-реализацию, не затрагивая основной код.
 */
interface AuditLog {
    /**
     * Ставит [event] в очередь на запись. Вызов не блокирует вызывающую корутину.
     * Если буфер очереди переполнен — реализация вправе дропнуть событие с warning-ом.
     */
    fun log(event: AuditEvent)
}
