package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientapp.ClientAppProfileSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppProfilesResponse
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInt
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * [ClientAppProfilesView] одним SQL: строки подключённых профилей и счётчик приглашений в каждой строке;
 * без профилей — одна строка только со счётчиком.
 */
class DbClientAppProfilesView : ClientAppProfilesView {
    context(ctx: ClientAccountContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun profiles(): ClientAppProfilesResponse {
        val rows =
            tr.sql(
                """
                WITH visible AS (
                    SELECT a.status, o.id AS org_id, o.name AS org_name, c.id AS client_id, c.name AS client_name
                    FROM client_app_accounts acc
                    JOIN client_app_access a ON a.phone_digits = acc.phone_digits AND a.status IN ('INVITED', 'CONNECTED')
                    JOIN organizations o ON o.id = a.org_id AND o.client_app_enabled
                    JOIN clients c ON c.id = a.client_id AND c.org_id = a.org_id AND c.state = 'ACTIVE'
                    WHERE acc.id = :accountId AND acc.deleted_at IS NULL
                ), invitations AS (
                    SELECT count(DISTINCT org_id)::int AS new_invitations FROM visible WHERE status = 'INVITED'
                )
                SELECT p.org_id, p.org_name, p.client_id, p.client_name, i.new_invitations
                FROM invitations i
                LEFT JOIN visible p ON p.status = 'CONNECTED'
                ORDER BY p.org_name, p.org_id, p.client_name, p.client_id
                """.trimIndent(),
            )
                .bind("accountId", ctx.accountId)
                .list { row ->
                    val profile =
                        row.asUuidOrNull("client_id")?.let {
                            ClientAppProfileSchema(row.asUuid("org_id").toOrgId(), row.asString("org_name"), it.toClientId(), row.asString("client_name"))
                        }
                    profile to row.asInt("new_invitations")
                }
        return ClientAppProfilesResponse(rows.mapNotNull { it.first }, rows.firstOrNull()?.second ?: 0)
    }
}
