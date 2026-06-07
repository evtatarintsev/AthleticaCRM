package org.athletica.crm.domain.discipline

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Каталог дисциплин (видов спорта) организации. */
interface Disciplines {
    /** Возвращает дисциплины организации, отсортированные по имени. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun list(): List<Discipline>

    /** Создаёт несохранённую дисциплину; запись в БД выполняется через [Discipline.save]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(id: DisciplineId, name: String): Discipline

    /** Возвращает дисциплину по идентификатору; ошибка `DISCIPLINE_NOT_FOUND`, если не найдена. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byId(id: DisciplineId): Discipline

    /**
     * Возвращает дисциплины по идентификаторам.
     * Дубликаты в [ids] игнорируются; ошибка `DISCIPLINE_NOT_FOUND`, если хотя бы одна не найдена.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byIds(ids: List<DisciplineId>): List<Discipline>
}
