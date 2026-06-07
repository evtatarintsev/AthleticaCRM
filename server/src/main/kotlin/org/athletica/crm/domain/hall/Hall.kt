package org.athletica.crm.domain.hall

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Зал филиала. */
interface Hall {
    /** Идентификатор зала. */
    val id: HallId

    /** Название зала. */
    val name: String

    /** Сохраняет зал: INSERT при создании либо UPDATE существующего. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()

    /** Удаляет зал; ошибка `HALL_IN_USE`, если он используется в расписании или занятиях. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete()

    /** Возвращает копию с новым именем (без записи в БД). */
    fun withNew(name: String): Hall
}
