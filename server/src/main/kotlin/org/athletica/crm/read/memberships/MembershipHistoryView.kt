package org.athletica.crm.read.memberships

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.memberships.MembershipHistoryResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция истории абонемента для панели абонемента.
 * Имя сотрудника-автора подставляется join'ом; запись без автора — действие системы.
 */
interface MembershipHistoryView {
    /**
     * Возвращает историю абонемента [membershipId] от новых записей к старым.
     * Ошибка, если абонемент не найден в организации.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byMembership(membershipId: MembershipId): MembershipHistoryResponse
}
