package org.athletica.crm.domain.audit

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.athletica.crm.db.Database
import kotlin.uuid.toJavaUuid

private val logger = KtorSimpleLogger("org.athletica.crm.audit.PostgresAuditLog")

/**
 * Сервис асинхронного логирования действий пользователей.
 *
 * Использует [Channel] с буфером для неблокирующего приёма событий.
 * Единственный consumer-корутин записывает события в БД последовательно —
 * это исключает конкурентные writes и упрощает добавление batching в будущем.
 *
 * При переполнении буфера событие дропается с предупреждением в лог:
 * основной запрос важнее аудита.
 *
 * [db] — соединение с базой данных.
 * [scope] — корутинный скоуп приложения (ApplicationScope); при его отмене consumer останавливается.
 */
class PostgresAuditLog(private val db: Database, scope: CoroutineScope) :
    AuditLog,
    AutoCloseable {
    private val channel = Channel<AuditEvent>(capacity = Channel.BUFFERED)

    init {
        scope.launch {
            for (event in channel) {
                try {
                    persist(event)
                } catch (e: Exception) {
                    logger.error("Failed to persist audit event: ${event.actionType}", e)
                }
            }
        }
    }

    /**
     * Отправляет событие в очередь. Вызов неблокирующий.
     * Если буфер переполнен — событие дропается, в лог пишется предупреждение.
     */
    override fun log(event: AuditEvent) {
        val result = channel.trySend(event)
        if (result.isFailure) {
            logger.warn("Audit channel is full, dropping event: ${event.actionType} by ${event.username}")
        }
    }

    /**
     * Закрывает channel, сигнализируя consumer-корутине завершить обработку оставшихся событий.
     * Вызывается при остановке приложения.
     */
    override fun close() {
        channel.close()
    }

    private suspend fun persist(event: AuditEvent) {
        db
            .sql(
                """
                INSERT INTO audit_logs (org_id, user_id, username, action_type, entity_type, entity_id, data, ip_address)
                VALUES (:orgId, :userId, :username, :actionType, :entityType, :entityId, :data::jsonb, :ipAddress)
                """.trimIndent(),
            ).bind("orgId", event.orgId.value.toJavaUuid())
            .bind("userId", event.userId?.value?.toJavaUuid())
            .bind("username", event.username)
            .bind("actionType", event.actionType.code)
            .bind("entityType", event.entityType)
            .bind("entityId", event.entityId?.toJavaUuid())
            .bind("data", event.data)
            .bind("ipAddress", event.ipAddress)
            .execute()
    }
}
