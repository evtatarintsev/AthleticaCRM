package org.athletica.crm.domain.hall

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Каталог залов филиала. Все операции ограничены текущим филиалом (`branch_id`). */
interface Halls {
    /** Возвращает залы текущего филиала, отсортированные по имени. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<Hall>

    /** Создаёт несохранённый зал; запись в БД выполняется через [Hall.save]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(id: HallId, name: String): Hall

    /** Возвращает зал по идентификатору; ошибка `HALL_NOT_FOUND`, если не найден. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: HallId): Hall

    /**
     * Возвращает залы по идентификаторам.
     * Дубликаты в [ids] игнорируются; ошибка `HALL_NOT_FOUND`, если хотя бы один не найден.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byIds(ids: List<HallId>): List<Hall>
}
