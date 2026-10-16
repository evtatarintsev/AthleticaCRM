package org.athletica.crm.read.sessions

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.attendance.SessionDebitPreviewResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция списаний абонементов для участников занятия: для проведённого — фактическое
 * списание, для запланированного — абонемент, который подберётся при проведении. Правило подбора —
 * то же, что у списания (`MembershipCandidateSql`).
 */
interface SessionDebitPreviewView {
    /** Списания участников занятия [sessionId]; ошибка, если занятие не найдено в организации. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun preview(sessionId: SessionId): SessionDebitPreviewResponse
}
