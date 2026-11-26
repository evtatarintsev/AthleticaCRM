package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import org.athletica.crm.api.schemas.clientapp.ClientAppSessionSchema
import org.athletica.crm.api.schemas.clientapp.ClientAppSessionsResponse
import org.athletica.crm.core.ClientAccountContext
import org.athletica.crm.core.entityids.toClientAppSessionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/** [ClientAppSessionsView] одним SQL по `client_app_sessions` действующего аккаунта. */
class DbClientAppSessionsView : ClientAppSessionsView {
    context(ctx: ClientAccountContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun sessions(query: ClientAppSessionsQuery): ClientAppSessionsResponse =
        ClientAppSessionsResponse(
            tr.sql(
                """
                SELECT s.id, s.name, s.created_at, s.last_seen_at
                FROM client_app_sessions s
                JOIN client_app_accounts acc ON acc.id = s.account_id AND acc.deleted_at IS NULL
                WHERE s.account_id = :accountId AND s.last_seen_at > :since
                ORDER BY s.last_seen_at DESC, s.id
                """.trimIndent(),
            )
                .bind("accountId", ctx.accountId)
                .bind("since", query.now - query.maxIdle)
                .list { row ->
                    val id = row.asUuid("id").toClientAppSessionId()
                    ClientAppSessionSchema(id, row.asStringOrNull("name"), row.asInstant("created_at"), row.asInstant("last_seen_at"), id == ctx.sessionId)
                },
        )
}
