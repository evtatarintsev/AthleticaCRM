package org.athletica.crm.domain.events

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.storage.Transaction
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * Запись о событии, прочитанная из таблицы [domain_events].
 *
 * [id] — идентификатор записи в outbox-таблице.
 * [orgId] — организация, в контексте которой произошло событие.
 * [eventType] — имя класса события (например, "GroupCreated").
 * [payload] — JSON-тело события, включая дискриминатор типа.
 * [retryCount] — количество неуспешных попыток обработки.
 */
data class DomainEventRecord(
    val id: Uuid,
    val orgId: OrgId,
    val eventType: String,
    val payload: String,
    val retryCount: Int,
)

/**
 * Шина доменных событий (outbox-паттерн).
 *
 * Публикация ([publish]) записывает событие в таблицу [domain_events] в рамках текущей транзакции.
 * [OrgId] берётся из [RequestContext], не требует явной передачи в событии.
 * Диспетчеризация ([dispatch]) вызывается воркером для каждой необработанной записи.
 * Регистрация обработчиков — через [on].
 */
class DomainEventBus : DomainEvents {
    private val logger = LoggerFactory.getLogger(DomainEventBus::class.java)

    @PublishedApi
    internal val json: Json = Json { ignoreUnknownKeys = true }

    @PublishedApi
    internal val handlers: MutableMap<String, MutableList<suspend (DomainEventRecord) -> Unit>> = mutableMapOf()

    /**
     * Регистрирует обработчик для событий типа [E].
     * Обработчик получает [OrgId] организации и десериализованное событие.
     * Один тип события может иметь несколько обработчиков.
     */
    inline fun <reified E : DomainEvent> on(crossinline handler: suspend (OrgId, E) -> Unit) {
        val typeName = E::class.simpleName!!
        handlers.getOrPut(typeName) { mutableListOf() }.add { record ->
            @Suppress("UNCHECKED_CAST")
            handler(record.orgId, json.decodeFromString<DomainEvent>(record.payload) as E)
        }
    }

    /**
     * Записывает [event] в outbox-таблицу в рамках текущей транзакции.
     * [OrgId] берётся из [ctx] и сохраняется в колонку `org_id`.
     * Обработчики будут вызваны асинхронно воркером после завершения транзакции.
     */
    context(ctx: RequestContext, tr: Transaction)
    override suspend fun publish(event: DomainEvent) {
        tr
            .sql(
                """
                INSERT INTO domain_events (id, org_id, event_type, payload)
                VALUES (:id, :orgId, :eventType, :payload::jsonb)
                """.trimIndent(),
            )
            .bind("id", Uuid.generateV7())
            .bind("orgId", ctx.orgId)
            .bind("eventType", event::class.simpleName!!)
            .bind("payload", json.encodeToString(event))
            .execute()
    }

    /**
     * Вызывается воркером: передаёт [record] всем зарегистрированным обработчикам.
     * Если обработчиков для типа события нет — пишет предупреждение и пропускает.
     * Выбрасывает исключение если хотя бы один обработчик завершился с ошибкой.
     */
    suspend fun dispatch(record: DomainEventRecord) {
        val eventHandlers = handlers[record.eventType]
        if (eventHandlers == null) {
            logger.warn("Нет обработчиков для события ${record.eventType}, id=${record.id}")
            return
        }
        eventHandlers.forEach { it(record) }
    }
}
