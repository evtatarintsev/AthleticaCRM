package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.storage.Transaction

/**
 * Абонементы клиентов организации.
 *
 * Абонемент выдаётся один раз и хранит собственный снимок параметров, поэтому правка
 * тарифа на ранее выданные абонементы не влияет. Списание занятий и корректировки пока
 * не поддерживаются.
 */
interface Memberships {
    /**
     * Создаёт несохранённый абонемент. Дата окончания вычисляется из [startDate] и срока
     * ([durationValue]/[durationUnit]); остаток занятий приравнивается к [sessions];
     * выдавший сотрудник берётся из контекста. Запись в БД — через [Membership.save].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun new(
        id: MembershipId,
        clientId: ClientId,
        tariffPlanId: TariffPlanId?,
        name: String,
        sessions: Int?,
        durationValue: Int,
        durationUnit: DurationUnit,
        startDate: LocalDate,
        price: Money,
    ): Membership

    /** Возвращает абонементы клиента, новые сверху. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun forClient(clientId: ClientId): List<Membership>
}
