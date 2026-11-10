package org.athletica.crm.domain.orgbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

interface OrgBalances {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun current(): OrgBalance
}
