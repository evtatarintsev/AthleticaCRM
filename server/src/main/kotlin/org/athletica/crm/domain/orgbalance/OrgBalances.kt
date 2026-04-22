package org.athletica.crm.domain.orgbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

interface OrgBalances {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun current(): OrgBalance
}
