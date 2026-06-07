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
 * R2DBC-реализация выданного абонемента. Все запросы фильтруются по `org_id`.
 * Валюта цены берётся из контекста организации, отдельной колонки нет.
 */
data class DbMembership(
    override val id: MembershipId,
    override val clientId: ClientId,
    override val tariffPlanId: TariffPlanId?,
    override val name: String,
    override val sessionsTotal: Int?,
    override val sessionsRemaining: Int?,
    override val startDate: LocalDate,
    override val endDate: LocalDate,
    override val price: Money,
    override val issuedBy: EmployeeId?,
) : Membership {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        tr.sql(
            """
            INSERT INTO memberships
                (id, org_id, client_id, tariff_plan_id, name,
                 sessions_total, sessions_remaining, start_date, end_date, price, issued_by)
            VALUES (:id, :orgId, :clientId, :tariffPlanId, :name,
                    :sessionsTotal, :sessionsRemaining, :startDate, :endDate, :price, :issuedBy)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("clientId", clientId)
            .bind("tariffPlanId", tariffPlanId)
            .bind("name", name)
            .bind("sessionsTotal", sessionsTotal)
            .bind("sessionsRemaining", sessionsRemaining)
            .bind("startDate", startDate)
            .bind("endDate", endDate)
            .bind("price", price)
            .bind("issuedBy", issuedBy)
            .execute()
    }
}
