package org.athletica.crm.domain.leadSource

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Источник привлечения клиента. */
interface LeadSource {
    /** Идентификатор источника. */
    val id: LeadSourceId

    /** Название источника. */
    val name: String

    /** Сохраняет источник: INSERT при создании либо UPDATE существующего. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    /** Удаляет источник. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete()

    /** Возвращает копию с новым именем (без записи в БД). */
    fun withNew(name: String): LeadSource
}
