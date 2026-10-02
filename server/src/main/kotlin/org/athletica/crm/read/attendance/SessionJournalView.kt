package org.athletica.crm.read.attendance

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.attendance.SessionJournalResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция карточки занятия с журналом посещаемости: данные занятия и его состав
 * с отметками, метками и видом участия.
 *
 * Состав запланированного занятия — строки журнала и клиенты с активной на дату занятия
 * записью в группу; проведённого и отменённого — только строки журнала.
 */
interface SessionJournalView {
    /** Журнал занятия [sessionId]; ошибка `SESSION_NOT_FOUND`, если занятия нет в организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun journal(sessionId: SessionId): SessionJournalResponse
}
