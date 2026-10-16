package org.athletica.crm.domain.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.actingEmployeeId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.TariffPlanId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.entityids.toTariffPlanId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.money.Money
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.core.subscription.ActivationReason
import org.athletica.crm.core.subscription.CloseMode
import org.athletica.crm.core.subscription.CloseReason
import org.athletica.crm.core.subscription.DurationUnit
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asMoney
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * R2DBC-реализация хранилища абонементов. Все запросы фильтруются по `org_id`.
 * Валюта цены берётся из контекста организации, отдельной колонки нет.
 */
class DbMemberships : Memberships {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun issue(
        id: MembershipId,
        clientId: ClientId,
        tariffPlanId: TariffPlanId?,
        name: String,
        sessions: Int?,
        duration: MembershipDuration,
        price: Money,
        activation: Activation,
        closeMode: CloseMode,
        issuedOn: LocalDate,
    ): Membership {
        val outcome = issueOutcome(activation, closeMode, sessions, issuedOn)
        val startDate = (outcome as? IssueOutcome.Active)?.startDate
        val endDate = startDate?.let { duration.endDateFrom(it) }
        val state = if (outcome is IssueOutcome.Active) MembershipState.ACTIVE else MembershipState.INACTIVE
        tr
            .sql(
                """
                INSERT INTO memberships
                    (id, org_id, client_id, tariff_plan_id, name, sessions_total, sessions_remaining,
                     start_date, end_date, price, issued_by, state, activation_mode, activate_on,
                     activation_deadline, duration_value, duration_unit, close_mode, issued_on)
                VALUES (:id, :orgId, :clientId, :tariffPlanId, :name, :sessions, :sessions,
                        :startDate, :endDate, :price, :issuedBy, :state, :activationMode, :activateOn,
                        :deadline, :durationValue, :durationUnit, :closeMode, :issuedOn)
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("clientId", clientId)
            .bind("tariffPlanId", tariffPlanId)
            .bind("name", name)
            .bind("sessions", sessions)
            .bind("startDate", startDate)
            .bind("endDate", endDate)
            .bind("price", price)
            .bind("issuedBy", ctx.actingEmployeeId)
            .bind("state", state.name)
            .bind("activationMode", activation.mode.name)
            .bind("activateOn", (activation as? Activation.OnDate)?.date)
            .bind("deadline", (activation as? Activation.OnFirstVisit)?.deadline)
            .bind("durationValue", duration.value)
            .bind("durationUnit", duration.unit.name)
            .bind("closeMode", closeMode.name)
            .bind("issuedOn", issuedOn)
            .execute()
        recordMembershipEvent(
            id,
            MembershipEventKind.ISSUED,
            buildJsonObject {
                put("activationMode", activation.mode.name)
                put("closeMode", closeMode.name)
                (activation as? Activation.OnDate)?.let { put("activateOn", it.date.toString()) }
                (activation as? Activation.OnFirstVisit)?.deadline?.let { put("activationDeadline", it.toString()) }
            },
        )
        if (startDate != null && endDate != null) {
            recordActivation(id, ActivationReason.ON_ISSUE, startDate, endDate)
        }
        return byId(id)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: MembershipId): Membership =
        tr
            .sql("$SELECT_MEMBERSHIP WHERE id = :id AND org_id = :orgId FOR UPDATE")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { it.toMembership() }
            ?: raise(CommonDomainError("MEMBERSHIP_NOT_FOUND", Messages.MembershipNotFound.localize()))

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun forClient(clientId: ClientId): List<Membership> =
        tr
            .sql("$SELECT_MEMBERSHIP WHERE client_id = :clientId AND org_id = :orgId ORDER BY created_at DESC, id DESC")
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .list { it.toMembership() }

    /** Собирает доменный абонемент нужного состояния из строки результата. */
    context(ctx: RequestContext)
    private fun Row.toMembership(): Membership {
        val terms =
            StoredMembershipTerms(
                id = asUuid("id").toMembershipId(),
                clientId = asUuid("client_id").toClientId(),
                tariffPlanId = asUuidOrNull("tariff_plan_id")?.toTariffPlanId(),
                name = asString("name"),
                sessionsTotal = get("sessions_total", Int::class.javaObjectType),
                duration = MembershipDuration(asInt("duration_value"), DurationUnit.valueOf(asString("duration_unit"))),
                price = asMoney("price", ctx.currency),
                closeMode = CloseMode.valueOf(asString("close_mode")),
                issuedOn = asLocalDate("issued_on"),
                issuedBy = asUuidOrNull("issued_by")?.toEmployeeId(),
            )
        val remaining = get("sessions_remaining", Int::class.javaObjectType)
        val activation =
            when (ActivationMode.valueOf(asString("activation_mode"))) {
                ActivationMode.IMMEDIATE -> Activation.Immediate
                ActivationMode.ON_DATE -> Activation.OnDate(asLocalDate("activate_on"))
                ActivationMode.ON_FIRST_VISIT -> Activation.OnFirstVisit(asLocalDateOrNull("activation_deadline"))
            }
        return when (MembershipState.valueOf(asString("state"))) {
            MembershipState.INACTIVE ->
                when (activation) {
                    is Activation.OnDate -> DbInactiveOnDate(terms, activation, remaining)
                    is Activation.OnFirstVisit -> DbInactiveOnFirstVisit(terms, activation, remaining)
                    Activation.Immediate -> error("Абонемент ${terms.id} с активацией «сразу» не может быть неактивированным")
                }
            MembershipState.ACTIVE ->
                DbActiveMembership(
                    terms = terms,
                    activation = activation,
                    sessionsRemaining = remaining,
                    startDate = asLocalDate("start_date"),
                    endDate = asLocalDate("end_date"),
                    closeNotifiedAt = asInstantOrNull("close_notified_at"),
                )
            MembershipState.CLOSED ->
                DbClosedMembership(
                    terms = terms,
                    activation = activation,
                    sessionsRemaining = remaining,
                    startDate = asLocalDate("start_date"),
                    endDate = asLocalDate("end_date"),
                    closedOn = asLocalDate("closed_on"),
                    closeReason = CloseReason.valueOf(asString("close_reason")),
                )
            MembershipState.REVOKED ->
                DbRevokedMembership(
                    terms = terms,
                    activation = activation,
                    sessionsRemaining = remaining,
                    startDate = asLocalDateOrNull("start_date"),
                    endDate = asLocalDateOrNull("end_date"),
                )
        }
    }

    private companion object {
        /** Колонки абонемента, из которых собирается доменный тип. */
        const val SELECT_MEMBERSHIP =
            """
            SELECT id, client_id, tariff_plan_id, name, sessions_total, sessions_remaining,
                   start_date, end_date, price, issued_by, state, activation_mode, activate_on,
                   activation_deadline, duration_value, duration_unit, close_mode, issued_on,
                   close_notified_at, closed_on, close_reason
            FROM memberships
            """
    }
}
