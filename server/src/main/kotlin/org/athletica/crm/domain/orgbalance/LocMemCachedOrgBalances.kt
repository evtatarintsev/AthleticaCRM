package org.athletica.crm.domain.orgbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.AdminRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.SystemRequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.storage.Transaction
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory кэш баланса организации поверх [delegate].
 *
 * Хранит результат [current] в [ConcurrentHashMap] на всё время жизни процесса.
 * При конкурентном промахе несколько корутин могут одновременно обратиться к [delegate]
 * и записать результат — это допустимо: лишний запрос безвреден, зато нет блокировок.
 *
 * После вызова [OrgBalance.adjust] кэш для соответствующей организации инвалидируется.
 *
 * **Память:** ~150 байт на организацию (ключ + объект баланса с пустым журналом).
 * При 2000 организаций — порядка 300 КБ, даже с заполненными журналами не более
 * единиц мегабайт.
 */
class LocMemCachedOrgBalances(private val delegate: OrgBalances) : OrgBalances {
    private val orgs: ConcurrentHashMap<OrgId, OrgBalance> = ConcurrentHashMap()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun current(): OrgBalance = orgs[ctx.orgId] ?: delegate.current().let { CachedOrgBalance(it) }.also { orgs[ctx.orgId] = it }

    private inner class CachedOrgBalance(private val inner: OrgBalance) : OrgBalance by inner {
        context(ctx: AdminRequestContext, tr: Transaction, raise: Raise<DomainError>)
        override suspend fun adjust(amount: Money, description: String): OrgBalance {
            val updated = inner.adjust(amount, description)
            orgs.remove(ctx.orgId)
            return CachedOrgBalance(updated)
        }

        context(ctx: SystemRequestContext, tr: Transaction, raise: Raise<DomainError>)
        override suspend fun replenish(amount: Money, description: String): OrgBalance {
            val updated = inner.replenish(amount, description)
            orgs.remove(ctx.orgId)
            return CachedOrgBalance(updated)
        }
    }
}
