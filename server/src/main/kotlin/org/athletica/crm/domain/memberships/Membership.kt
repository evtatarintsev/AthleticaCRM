package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.storage.Transaction

/**
 * Выданный абонемент — экземпляр, хранящий собственный снимок параметров на момент выдачи.
 * В отличие от тарифа-шаблона ([org.athletica.crm.domain.tariffs.TariffPlan]) не редактируется:
 * абонемент append-only, списания занятий и корректировки пока не реализованы.
 */
interface Membership {
    /** Идентификатор абонемента. */
    val id: MembershipId

    /** Клиент-владелец абонемента. */
    val clientId: ClientId

    /** Тариф-шаблон, на основе которого выдан абонемент; `null` — индивидуальный. */
    val tariffPlanId: TariffPlanId?

    /** Название (снимок на момент выдачи). */
    val name: String

    /** Общее количество занятий; `null` — безлимит. */
    val sessionsTotal: Int?

    /** Остаток занятий; `null` — безлимит. */
    val sessionsRemaining: Int?

    /** Дата начала действия. */
    val startDate: LocalDate

    /** Дата окончания действия. */
    val endDate: LocalDate

    /** Стоимость на момент выдачи (без учёта оплаты/баланса). */
    val price: Money

    /** Сотрудник, выдавший абонемент; `null`, если сотрудник удалён. */
    val issuedBy: EmployeeId?

    /** Сохраняет абонемент (INSERT). Абонемент неизменяем, повторная запись не предполагается. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun save()
}
