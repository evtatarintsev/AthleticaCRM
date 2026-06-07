package org.athletica.crm.domain.discipline

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Дисциплина (вид спорта). */
interface Discipline {
    /** Идентификатор дисциплины. */
    val id: DisciplineId

    /** Название дисциплины. */
    val name: String

    /** Сохраняет дисциплину: INSERT при создании либо UPDATE существующей. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    /** Удаляет дисциплину. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete()

    /** Возвращает копию с новым именем (без записи в БД). */
    fun withNew(name: String): Discipline
}
