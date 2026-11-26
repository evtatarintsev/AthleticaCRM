package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppInvitationsResponse
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** [ClientAppInvitationsView] одним SQL по доступам номера аккаунта. */
class DbClientAppInvitationsView : ClientAppInvitationsView {
    context(ctx: ClientAccountContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun invitations(): ClientAppInvitationsResponse =
        ClientAppInvitationsResponse(
            tr.sql(
                """
                SELECT o.id AS org_id, o.name AS org_name, max(a.invited_at) AS invited_at, count(*)::int AS client_count
                FROM client_app_accounts acc
                JOIN client_app_access a ON a.phone_digits = acc.phone_digits AND a.status = 'INVITED'
                JOIN organizations o ON o.id = a.org_id AND o.client_app_enabled
                JOIN clients c ON c.id = a.client_id AND c.org_id = a.org_id AND c.state = 'ACTIVE'
                WHERE acc.id = :accountId AND acc.deleted_at IS NULL
                GROUP BY o.id, o.name
                ORDER BY invited_at DESC, o.id
                """.trimIndent(),
            )
                .bind("accountId", ctx.accountId)
                .list {
                    ClientAppInvitationSchema(it.asUuid("org_id").toOrgId(), it.asString("org_name"), it.asInstant("invited_at"), it.asInt("client_count"))
                },
        )
}
