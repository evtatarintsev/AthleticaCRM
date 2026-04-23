package org.athletica.crm.domain.org

import arrow.core.raise.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory кэш организации поверх [delegate].
 *
 * Хранит [Organization] в [ConcurrentHashMap] на всё время жизни процесса.
 * При конкурентном промахе возможен лишний запрос к [delegate] — допустимо,
 * блокировок нет.
 *
 * **Инвалидация:** происходит в [Organization.save] до коммита транзакции.
 * Если транзакция откатится — кэш будет излишне сброшен, следующий запрос
 * просто перечитает данные из БД. Для настроек организации это приемлемо.
 *
 * **Память:** ~несколько сотен байт на организацию.
 */
class LocMemCachedOrganizations(private val delegate: Organizations) : Organizations {
    private val cache = ConcurrentHashMap<OrgId, Organization>()

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun current(): Organization =
        cache[ctx.orgId] ?: CachedOrganization(delegate.current(), cache)
            .also { cache[ctx.orgId] = it }
}

/**
 * Прокси над [delegate], который при [save] инвалидирует запись в [cache].
 */
class CachedOrganization(
    private val delegate: Organization,
    private val cache: ConcurrentHashMap<OrgId, Organization>,
) : Organization by delegate {
    context(ctx: RequestContext, raise: Raise<DomainError>)
    override suspend fun withNew(newName: String, newTimezone: String) = CachedOrganization(delegate.withNew(newName, newTimezone), cache)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        delegate.save()
        cache.remove(delegate.id)
    }
}
