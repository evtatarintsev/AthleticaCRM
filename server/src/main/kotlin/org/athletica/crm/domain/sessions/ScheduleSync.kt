package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Сверка расписания и занятий: приводит занятия организации в соответствие
 * с действующими версиями слотов в пределах горизонта материализации.
 *
 * Горизонт — свойство системы, а не параметр вызова: границы у метода нет,
 * поэтому никакой запрос не может заставить систему материализовать занятия дальше.
 * Занятия, которых касался человек, сверка не трогает никогда.
 */
interface ScheduleSync {
    /** Приводит занятия организации в соответствие с расписанием. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun sync()
}
