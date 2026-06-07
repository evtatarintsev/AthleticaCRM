package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.entityids.toTariffPlanId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * R2DBC-реализация хранилища абонементов. Все запросы фильтруются по `org_id`.
 * Валюта цены берётся из контекста организации, отдельной колонки нет.
 */
class DbMemberships : Memberships {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: MembershipId,
        clientId: ClientId,
        tariffPlanId: TariffPlanId?,
        name: String,
        sessions: Int?,
        durationValue: Int,
        durationUnit: DurationUnit,
        startDate: LocalDate,
        price: Money,
    ): Membership =
        DbMembership(
            id = id,
            clientId = clientId,
            tariffPlanId = tariffPlanId,
            name = name,
            sessionsTotal = sessions,
            sessionsRemaining = sessions,
            startDate = startDate,
            endDate = endDate(startDate, durationValue, durationUnit),
            price = price,
            issuedBy = ctx.employeeId,
        )

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun forClient(clientId: ClientId): List<Membership> =
        tr.sql(
            """
            SELECT id, client_id, tariff_plan_id, name,
                   sessions_total, sessions_remaining, start_date, end_date, price, issued_by
            FROM memberships
            WHERE client_id = :clientId AND org_id = :orgId
            ORDER BY created_at DESC
            """.trimIndent(),
        )
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .list { it.toMembership() }

    /** Собирает доменный абонемент из строки результата. */
    context(ctx: EmployeeRequestContext)
    private fun Row.toMembership(): Membership =
        DbMembership(
            id = asUuid("id").toMembershipId(),
            clientId = asUuid("client_id").toClientId(),
            tariffPlanId = asUuidOrNull("tariff_plan_id")?.toTariffPlanId(),
            name = asString("name"),
            sessionsTotal = get("sessions_total", Int::class.javaObjectType),
            sessionsRemaining = get("sessions_remaining", Int::class.javaObjectType),
            startDate = asLocalDate("start_date"),
            endDate = asLocalDate("end_date"),
            price = asMoney("price", ctx.currency),
            issuedBy = asUuidOrNull("issued_by")?.toEmployeeId(),
        )
}

/** Вычисляет дату окончания абонемента от [startDate] на [value] [unit]. */
private fun endDate(startDate: LocalDate, value: Int, unit: DurationUnit): LocalDate =
    when (unit) {
        DurationUnit.DAYS -> startDate.plus(DatePeriod(days = value))
        DurationUnit.MONTHS -> startDate.plus(DatePeriod(months = value))
    }
