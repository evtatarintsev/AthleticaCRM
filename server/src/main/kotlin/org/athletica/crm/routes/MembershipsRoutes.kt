package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.schemas.memberships.ActiveMembershipStateSchema
import org.athletica.crm.api.schemas.memberships.CloseMembershipRequest
import org.athletica.crm.api.schemas.memberships.ClosedMembershipStateSchema
import org.athletica.crm.api.schemas.memberships.InactiveMembershipStateSchema
import org.athletica.crm.api.schemas.memberships.IssueMembershipRequest
import org.athletica.crm.api.schemas.memberships.MembershipHistoryRequest
import org.athletica.crm.api.schemas.memberships.MembershipHistoryResponse
import org.athletica.crm.api.schemas.memberships.MembershipListRequest
import org.athletica.crm.api.schemas.memberships.MembershipListResponse
import org.athletica.crm.api.schemas.memberships.MembershipSchema
import org.athletica.crm.api.schemas.memberships.MembershipStateSchema
import org.athletica.crm.api.schemas.memberships.RevokeMembershipRequest
import org.athletica.crm.api.schemas.memberships.RevokedMembershipStateSchema
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.subscription.ActivationMode
import org.athletica.crm.domain.memberships.Activation
import org.athletica.crm.domain.memberships.ActiveMembership
import org.athletica.crm.domain.memberships.ClosedMembership
import org.athletica.crm.domain.memberships.InactiveMembership
import org.athletica.crm.domain.memberships.Membership
import org.athletica.crm.domain.memberships.MembershipDuration
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.domain.memberships.RevokedMembership
import org.athletica.crm.domain.org.OrgCalendar
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.ReadViews
import org.athletica.crm.storage.Database
import org.athletica.crm.usecases.memberships.closeMembership
import org.athletica.crm.usecases.memberships.revokeMembership

/**
 * Маршруты выданных абонементов (требуют JWT-авторизации).
 * «Сегодня» — по часовому поясу организации из [calendar].
 */
context(db: Database)
fun RouteWithContext.membershipsRoutes(memberships: Memberships, calendar: OrgCalendar, views: ReadViews) {
    route("/memberships") {
        post<IssueMembershipRequest, Unit>("/issue") { request ->
            db.transaction {
                memberships.issue(
                    id = request.id,
                    clientId = request.clientId,
                    tariffPlanId = request.tariffPlanId,
                    name = request.name,
                    sessions = request.sessions,
                    duration = MembershipDuration(request.durationValue, request.durationUnit),
                    price = request.price,
                    activation = request.activation(),
                    closeMode = request.closeMode,
                    issuedOn = calendar.today(),
                )
            }
        }

        post<MembershipListRequest, MembershipListResponse>("/list") { request ->
            db.transaction {
                val today = calendar.today()
                memberships.forClient(request.clientId)
                    .map { it.toSchema(today) }
                    .let { MembershipListResponse(it) }
            }
        }

        post<MembershipHistoryRequest, MembershipHistoryResponse>("/history") { request ->
            db.transaction { views.membershipHistory.byMembership(request.membershipId) }
        }

        post<CloseMembershipRequest, Unit>("/close") { request ->
            db.transaction { closeMembership(memberships, calendar, request.membershipId) }
        }

        post<RevokeMembershipRequest, Unit>("/revoke") { request ->
            db.transaction { revokeMembership(memberships, request.membershipId) }
        }
    }
}

/** Режим активации запроса с его датой; дата обязательна для режима «с даты». */
context(ctx: RequestContext, raise: Raise<DomainError>)
private fun IssueMembershipRequest.activation(): Activation =
    when (activationMode) {
        ActivationMode.IMMEDIATE -> Activation.Immediate
        ActivationMode.ON_DATE ->
            Activation.OnDate(
                activateOn ?: raise(CommonDomainError("ACTIVATION_DATE_REQUIRED", Messages.MembershipActivationDateRequired.localize())),
            )
        ActivationMode.ON_FIRST_VISIT -> Activation.OnFirstVisit(activationDeadline)
    }

/** Преобразует доменный абонемент в схему ответа со свойствами, выведенными на дату [today]. */
private fun Membership.toSchema(today: LocalDate): MembershipSchema =
    MembershipSchema(
        id = id,
        name = name,
        tariffPlanId = tariffPlanId,
        sessionsTotal = sessionsTotal,
        sessionsRemaining = sessionsRemaining,
        durationValue = duration.value,
        durationUnit = duration.unit,
        price = price,
        activationMode = activation.mode,
        activationDeadline = (activation as? Activation.OnFirstVisit)?.deadline,
        closeMode = closeMode,
        issuedOn = issuedOn,
        state = stateSchema(today),
    )

/** Состояние абонемента для ответа; для активного — свойства на дату [today]. */
private fun Membership.stateSchema(today: LocalDate): MembershipStateSchema =
    when (this) {
        is InactiveMembership -> InactiveMembershipStateSchema(activateOn = (activation as? Activation.OnDate)?.date)
        is ActiveMembership -> {
            val status = statusAt(today)
            ActiveMembershipStateSchema(
                startDate = startDate,
                endDate = endDate,
                termPosition = status.termPosition,
                endingSoon = status.endingSoon.sorted(),
                closePending = status.closePending,
            )
        }
        is ClosedMembership -> ClosedMembershipStateSchema(startDate, endDate, closedOn, closeReason)
        is RevokedMembership -> RevokedMembershipStateSchema(startDate, endDate)
    }
