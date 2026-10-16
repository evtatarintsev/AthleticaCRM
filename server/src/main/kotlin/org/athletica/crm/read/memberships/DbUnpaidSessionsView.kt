package org.athletica.crm.read.memberships

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.memberships.UnpaidSessionSchema
import org.athletica.crm.api.schemas.memberships.UnpaidSessionsResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.MembershipId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [UnpaidSessionsView] одним SQL по журналу, группам абонемента и списаниям. */
class DbUnpaidSessionsView : UnpaidSessionsView {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byMembership(membershipId: MembershipId): UnpaidSessionsResponse {
        tr
            .sql("SELECT 1 FROM memberships WHERE id = :id AND org_id = :orgId")
            .bind("id", membershipId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { 1 }
            ?: raise(CommonDomainError("MEMBERSHIP_NOT_FOUND", Messages.MembershipNotFound.localize()))
        val sessions =
            tr
                .sql(
                    """
                    SELECT s.id, s.date, s.start_time, g.id AS group_id, g.name AS group_name
                      FROM memberships m
                      JOIN session_attendance a ON a.client_id = m.client_id AND a.presence = 'present'
                      JOIN sessions s ON s.id = a.session_id AND s.org_id = m.org_id AND s.status = 'completed'
                      JOIN membership_groups mg ON mg.membership_id = m.id AND mg.group_id = s.group_id
                      JOIN groups g ON g.id = s.group_id
                     WHERE m.id = :id AND m.org_id = :orgId
                       AND NOT EXISTS (SELECT 1 FROM membership_debits d
                                        WHERE d.session_id = s.id AND d.client_id = m.client_id
                                          AND d.reversed_at IS NULL)
                     ORDER BY s.date, s.start_time, s.id
                    """.trimIndent(),
                )
                .bind("id", membershipId)
                .bind("orgId", ctx.orgId)
                .list {
                    UnpaidSessionSchema(
                        sessionId = it.asUuid("id").toSessionId(),
                        date = it.asLocalDate("date"),
                        startTime = it.asLocalTime("start_time"),
                        groupId = it.asUuid("group_id").toGroupId(),
                        groupName = it.asString("group_name"),
                    )
                }
        return UnpaidSessionsResponse(sessions)
    }
}
