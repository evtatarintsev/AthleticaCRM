package org.athletica.crm.read.clientapp

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.api.schemas.clientapp.ClientAppHomeResponse
import org.athletica.crm.core.ClientProfileContext
import org.athletica.crm.core.ClientProfileContexts
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString

/** [ClientAppHomeView] одним SQL с проверкой подключённого доступа. */
class DbClientAppHomeView : ClientAppHomeView {
    context(ctx: ClientProfileContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun home(): ClientAppHomeResponse =
        tr.sql(
            """
            SELECT c.name AS client_name, o.name AS org_name
            FROM clients c JOIN organizations o ON o.id = c.org_id
            WHERE c.id = :clientId AND c.org_id = :orgId AND EXISTS (${ClientProfileContexts.CONNECTED_ACCESS_IDS})
            """.trimIndent(),
        )
            .bind("accountId", ctx.accountId)
            .bind("orgId", ctx.orgId)
            .bind("clientId", ctx.clientId)
            .firstOrNull { ClientAppHomeResponse(it.asString("client_name"), it.asString("org_name")) }
            ?: raise(ClientProfileContexts.clientNotFound(ctx.lang))
}
