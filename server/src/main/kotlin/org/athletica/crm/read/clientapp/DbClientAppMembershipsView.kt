package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.datetime.LocalDate
import org.athletica.crm.api.appJson
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipDebitSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipFreezeSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipState
import org.athletica.crm.api.schemas.clientapp.ClientAppMembershipsResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.entityids.toMembershipId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.subscription.ActiveStatus
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asIntOrNull
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * [ClientAppMembershipsView]: «сегодня» организации с проверкой подключённого доступа, затем один SQL
 * по абонементам клиента с действующими списаниями. Признаки «заканчивается» и «закончился» выводит общий
 * [ActiveStatus.of] в Kotlin — ответ непостраничный и небольшой.
 */
class DbClientAppMembershipsView : ClientAppMembershipsView {
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun memberships(query: ClientAppMembershipsQuery): ClientAppMembershipsResponse {
        val today =
            tr.sql(TODAY_SQL)
                .bind("now", query.now)
                .bind("accountId", ctx.accountId)
                .bind("orgId", ctx.orgId)
                .bind("clientId", ctx.clientId)
                .firstOrNull { it.asLocalDate("today") }
                ?: raise(ClientProfileContexts.clientNotFound(ctx.lang))
        val memberships =
            tr.sql(SQL)
                .bind("orgId", ctx.orgId)
                .bind("clientId", ctx.clientId)
                .list { it.toMembership(today) }
        return ClientAppMembershipsResponse(memberships)
    }

    /** Абонемент из строки выборки с признаком «заканчивается» на дату [today]. */
    private fun Row.toMembership(today: LocalDate): ClientAppMembershipSchema {
        val state = ClientAppMembershipState.valueOf(asString("state"))
        val start = asLocalDateOrNull("start_date")
        val end = asLocalDateOrNull("end_date")
        val remaining = asIntOrNull("sessions_remaining")
        val freezeStart = asLocalDateOrNull("freeze_start")
        val freezeEnd = asLocalDateOrNull("freeze_end")?.takeIf { it >= today }
        val status =
            if (state == ClientAppMembershipState.ACTIVE && start != null && end != null) {
                ActiveStatus.of(start, end, remaining, today)
            } else {
                null
            }
        return ClientAppMembershipSchema(
            id = asUuid("id").toMembershipId(),
            name = asString("name"),
            state = state,
            sessionsTotal = asIntOrNull("sessions_total"),
            sessionsRemaining = remaining,
            startDate = start,
            endDate = end,
            activateOn = asLocalDateOrNull("activate_on").takeIf { state == ClientAppMembershipState.INACTIVE },
            endingSoon = status?.endingSoon?.sorted().orEmpty(),
            ended = status?.closePending ?: false,
            freeze = if (freezeStart != null && freezeEnd != null) ClientAppMembershipFreezeSchema(freezeStart, freezeEnd) else null,
            closedOn = asLocalDateOrNull("closed_on"),
            debits = appJson.decodeFromString<List<ClientAppMembershipDebitSchema>>(asString("debits")),
        )
    }

    private companion object {
        val TODAY_SQL =
            """
            SELECT (CAST(:now AS timestamptz) AT TIME ZONE o.timezone)::date AS today
            FROM organizations o
            WHERE o.id = :orgId AND EXISTS (${ClientProfileContexts.CONNECTED_ACCESS_IDS})
            """.trimIndent()

        val SQL =
            """
            SELECT m.id, m.name, m.state, m.sessions_total, m.sessions_remaining, m.start_date, m.end_date,
                   m.activate_on, m.freeze_start, m.freeze_end, m.closed_on,
                   COALESCE((
                       SELECT jsonb_agg(jsonb_build_object('sessionId', s.id, 'sessionDate', s.date, 'groupName', g.name)
                                        ORDER BY s.date DESC, s.start_time DESC, d.id DESC)
                       FROM membership_debits d
                       JOIN sessions s ON s.id = d.session_id AND s.org_id = m.org_id
                       LEFT JOIN groups g ON g.id = s.group_id
                       WHERE d.membership_id = m.id AND d.reversed_at IS NULL
                   ), '[]')::text AS debits
            FROM memberships m
            WHERE m.org_id = :orgId AND m.client_id = :clientId
            ORDER BY CASE m.state WHEN 'ACTIVE' THEN 0 WHEN 'INACTIVE' THEN 1 ELSE 2 END,
                     m.issued_on DESC, m.created_at DESC, m.id DESC
            """.trimIndent()
    }
}
