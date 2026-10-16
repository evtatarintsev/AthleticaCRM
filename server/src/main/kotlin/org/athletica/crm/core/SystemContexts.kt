package org.athletica.crm.core

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.toCurrency
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/**
 * Единственная фабрика [SystemRequestContext] для системных фоновых операций
 * (планировщики, воркеры, обработчики событий).
 *
 * Валюта читается из `organizations`: у контекста нет валюты по умолчанию, чтобы
 * организация не в рублях не получала молча неверную валюту в [org.athletica.crm.core.money.Money].
 */
object SystemContexts {
    /**
     * Собирает системный контекст организации [orgId] с валютой из БД.
     * [branchId] — целевой филиал операции; `null` для операций на всю организацию.
     */
    context(tr: Transaction, raise: Raise<DomainError>)
    suspend fun forOrg(orgId: OrgId, branchId: BranchId? = null): SystemRequestContext {
        val currency =
            tr
                .sql("SELECT currency FROM organizations WHERE id = :orgId")
                .bind("orgId", orgId)
                .firstOrNull { it.asString("currency").trim().toCurrency() }
                ?: raise(CommonDomainError("ORG_NOT_FOUND", "Organization $orgId not found"))
        return SystemRequestContext(
            lang = Lang.RU,
            orgId = orgId,
            currency = currency,
            branchId = branchId,
        )
    }
}
