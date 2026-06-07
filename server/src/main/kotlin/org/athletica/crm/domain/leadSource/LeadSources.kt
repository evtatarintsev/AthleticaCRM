package org.athletica.crm.domain.leadSource

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Каталог источников привлечения клиентов организации. */
interface LeadSources {
    /** Возвращает все источники организации, отсортированные по имени. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<LeadSource>

    /** Создаёт несохранённый источник; запись в БД выполняется через [LeadSource.save]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(id: LeadSourceId, name: String): LeadSource

    /** Возвращает источник по идентификатору; ошибка `LEAD_SOURCE_NOT_FOUND`, если не найден. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: LeadSourceId): LeadSource

    /**
     * Возвращает источники по идентификаторам.
     * Дубликаты в [ids] игнорируются; ошибка `LEAD_SOURCE_NOT_FOUND`, если хотя бы один не найден.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byIds(ids: List<LeadSourceId>): List<LeadSource>
}
