package org.athletica.crm.read.memberships

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.memberships.UnpaidSessionsResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Read-проекция неоплаченных занятий клиента в группах абонемента: проведённые занятия
 * с отметкой «пришёл», за которые нет действующего списания. Используется панелью погашения
 * долга и проверкой долгов после системной активации абонемента (поэтому контекст — любой
 * [RequestContext], включая системный).
 */
interface UnpaidSessionsView {
    /**
     * Неоплаченные занятия клиента абонемента [membershipId] в его группах, от старых к новым.
     * Ошибка, если абонемент не найден в организации.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byMembership(membershipId: MembershipId): UnpaidSessionsResponse
}
