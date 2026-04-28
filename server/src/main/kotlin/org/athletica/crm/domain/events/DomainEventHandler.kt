package org.athletica.crm.domain.events

import org.athletica.crm.core.entityids.OrgId

/**
 * Обработчик доменного события типа [E].
 *
 * Обработчики вызываются асинхронно [DomainEventWorker] после завершения транзакции,
 * в которой событие было опубликовано. Каждый обработчик выполняется в своей транзакции.
 *
 * @param E тип обрабатываемого события
 */
interface DomainEventHandler<in E : DomainEvent> {
    /**
     * Обрабатывает событие [event] для организации [orgId].
     *
     * @param orgId идентификатор организации, в контексте которой произошло событие
     * @param event десериализованное событие
     */
    suspend fun handle(orgId: OrgId, event: E)
}
