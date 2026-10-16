package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ChannelBindingId
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация хранилища связок каналов на PostgreSQL через R2DBC. */
class DbChannelBindings : ChannelBindings {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun link(integrationId: ChannelIntegrationId, clientId: ClientId, address: String): ChannelBinding {
        val clientExists =
            tr.sql("SELECT EXISTS (SELECT 1 FROM clients WHERE id = :clientId AND org_id = :orgId) AS found")
                .bind("clientId", clientId)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row -> row.asBoolean("found") } ?: false
        ensure(clientExists) { CommonDomainError("NOT_FOUND", Messages.ClientNotFound.localize()) }

        val id = ChannelBindingId.new()
        val inserted =
            tr.sql(
                """
                INSERT INTO channel_bindings (id, org_id, integration_id, client_id, address)
                VALUES (:id, :orgId, :integrationId, :clientId, :address)
                ON CONFLICT (integration_id, address) DO NOTHING
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("integrationId", integrationId)
                .bind("clientId", clientId)
                .bind("address", address)
                .execute()
        ensure(inserted != 0L) { CommonDomainError("CHAT_ALREADY_LINKED", Messages.ChatAlreadyLinked.localize()) }

        return ChannelBinding(id = id, integrationId = integrationId, clientId = clientId, address = address)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
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

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun addressFor(integrationId: ChannelIntegrationId, clientId: ClientId): String? =
        tr.sql(
            """
            SELECT address
            FROM channel_bindings
            WHERE integration_id = :integrationId AND client_id = :clientId AND org_id = :orgId
            ORDER BY last_inbound_at DESC NULLS LAST, created_at
            LIMIT 1
            """.trimIndent(),
        )
            .bind("integrationId", integrationId)
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.asString("address") }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun touchInbound(integrationId: ChannelIntegrationId, address: String) {
        tr.sql(
            """
            UPDATE channel_bindings
            SET last_inbound_at = clock_timestamp()
            WHERE integration_id = :integrationId AND address = :address AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("integrationId", integrationId)
            .bind("address", address)
            .bind("orgId", ctx.orgId)
            .execute()
    }
}
