package org.athletica.crm.domain.branch

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Филиал организации. */
interface Branch {
    /** Идентификатор филиала. */
    val id: BranchId

    /** Название филиала. */
    val name: String

    /** Сохраняет филиал: INSERT при создании либо UPDATE существующего. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    /** Удаляет филиал. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete()

    /** Возвращает копию с новым именем (без записи в БД). */
    fun withNew(name: String): Branch
}
