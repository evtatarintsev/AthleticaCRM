package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelBindingId
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.toChannelBindingId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация хранилища связок каналов на PostgreSQL через R2DBC. */
class DbChannelBindings : ChannelBindings {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun link(integrationId: ChannelIntegrationId, clientId: ClientId, address: String): ChannelBinding {
        tr.sql(
            """
            INSERT INTO channel_bindings (id, org_id, integration_id, client_id, address)
            VALUES (:id, :orgId, :integrationId, :clientId, :address)
            ON CONFLICT (integration_id, address) DO UPDATE SET client_id = excluded.client_id
            """.trimIndent(),
        )
            .bind("id", ChannelBindingId.new())
            .bind("orgId", ctx.orgId)
            .bind("integrationId", integrationId)
            .bind("clientId", clientId)
            .bind("address", address)
            .execute()

        return tr.sql(
            """
            SELECT id, integration_id, client_id, address
            FROM channel_bindings
            WHERE integration_id = :integrationId AND address = :address AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("integrationId", integrationId)
            .bind("address", address)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                ChannelBinding(
                    id = row.asUuid("id").toChannelBindingId(),
                    integrationId = integrationId,
                    clientId = row.asUuid("client_id").toClientId(),
                    address = row.asString("address"),
                )
            }!!
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun clientFor(integrationId: ChannelIntegrationId, address: String): ClientId? =
        tr.sql(
            """
            SELECT client_id
            FROM channel_bindings
            WHERE integration_id = :integrationId AND address = :address AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("integrationId", integrationId)
            .bind("address", address)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.asUuid("client_id").toClientId() }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun addressFor(integrationId: ChannelIntegrationId, clientId: ClientId): String? =
        tr.sql(
            """
            SELECT address
            FROM channel_bindings
            WHERE integration_id = :integrationId AND client_id = :clientId AND org_id = :orgId
            ORDER BY created_at
            LIMIT 1
            """.trimIndent(),
        )
            .bind("integrationId", integrationId)
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.asString("address") }
}
