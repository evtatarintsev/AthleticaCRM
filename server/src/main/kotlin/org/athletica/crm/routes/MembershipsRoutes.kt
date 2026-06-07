package org.athletica.crm.routes

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.athletica.crm.api.schemas.memberships.IssueMembershipRequest
import org.athletica.crm.api.schemas.memberships.MembershipListRequest
import org.athletica.crm.api.schemas.memberships.MembershipListResponse
import org.athletica.crm.api.schemas.memberships.MembershipSchema
import org.athletica.crm.core.subscription.MembershipStatus
import org.athletica.crm.domain.memberships.Membership
import org.athletica.crm.domain.memberships.Memberships
import org.athletica.crm.storage.Database
import kotlin.time.Clock

/**
 * Маршруты выданных абонементов (требуют JWT-авторизации).
 */
context(db: Database)
fun RouteWithContext.membershipsRoutes(memberships: Memberships) {
    route("/memberships") {
        post<IssueMembershipRequest, Unit>("/issue") { request ->
            db.transaction {
                memberships.new(
                    id = request.id,
                    clientId = request.clientId,
                    tariffPlanId = request.tariffPlanId,
                    name = request.name,
                    sessions = request.sessions,
                    durationValue = request.durationValue,
                    durationUnit = request.durationUnit,
                    startDate = request.startDate,
                    price = request.price,
                ).save()
            }
        }

        post<MembershipListRequest, MembershipListResponse>("/list") { request ->
            db.transaction {
                memberships.forClient(request.clientId)
                    .map { it.toSchema() }
                    .let { MembershipListResponse(it) }
            }
        }
    }
}

/** Преобразует доменный абонемент в схему ответа, вычисляя статус по дате окончания. */
private fun Membership.toSchema(): MembershipSchema {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val status = if (endDate < today) MembershipStatus.EXPIRED else MembershipStatus.ACTIVE
    return MembershipSchema(
        id = id,
        name = name,
        sessionsTotal = sessionsTotal,
        sessionsRemaining = sessionsRemaining,
        startDate = startDate,
        endDate = endDate,
        price = price,
        status = status,
    )
}
