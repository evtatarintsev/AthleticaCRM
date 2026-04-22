package org.athletica.crm.domain.orgbalance

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory кэш баланса организации поверх [delegate].
 *
 * Хранит результат [current] в [ConcurrentHashMap] на всё время жизни процесса.
 * При конкурентном промахе несколько корутин могут одновременно обратиться к [delegate]
 * и записать результат — это допустимо: лишний запрос безвреден, зато нет блокировок.
 *
 * **Инвалидация:** кэш намеренно не инвалидируется, поскольку сейчас нет write-пути
 * в [org_balance_journal]. При добавлении операций изменения баланса — вызвать
 * `orgs.remove(orgId)` в точке записи.
 *
 * **Память:** ~150 байт на организацию (ключ + объект баланса с пустым журналом).
 * При 2000 организаций — порядка 300 КБ, даже с заполненными журналами не более
 * единиц мегабайт.
 */
class LocMemCachedOrgBalances(private val delegate: OrgBalances) : OrgBalances {
    private val orgs: ConcurrentHashMap<OrgId, OrgBalance> = ConcurrentHashMap()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun current(): OrgBalance =
        orgs[ctx.orgId] ?: delegate.current().also { orgs[ctx.orgId] = it }
}
