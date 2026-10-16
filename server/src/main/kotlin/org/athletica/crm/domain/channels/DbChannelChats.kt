package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.toChannelIntegrationId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import kotlin.uuid.Uuid

/** Реализация хранилища чатов связанных каналов на PostgreSQL через R2DBC. */
class DbChannelChats : ChannelChats {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun upsert(
        integrationId: ChannelIntegrationId,
        address: String,
        displayName: String,
        username: String?,
        phone: String?,
    ): ChannelChat =
        tr.sql(
            """
            INSERT INTO channel_chats (id, org_id, integration_id, address, display_name, username, phone)
            VALUES (:id, :orgId, :integrationId, :address, :displayName, :username, :phone)
            ON CONFLICT (integration_id, address) DO UPDATE
            SET display_name = excluded.display_name,
                username = excluded.username,
                phone = COALESCE(excluded.phone, channel_chats.phone)
            RETURNING integration_id, address, display_name, username, phone
            """.trimIndent(),
        )
            .bind("id", Uuid.generateV7())
            .bind("orgId", ctx.orgId)
            .bind("integrationId", integrationId)
            .bind("address", address)
            .bind("displayName", displayName)
            .bind("username", username)
            .bind("phone", phone)
            .firstOrNull { row -> row.toChat() }!!

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byAddress(integrationId: ChannelIntegrationId, address: String): ChannelChat? =
        tr.sql(
            """
            SELECT integration_id, address, display_name, username, phone
            FROM channel_chats
            WHERE integration_id = :integrationId AND address = :address AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("integrationId", integrationId)
            .bind("address", address)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toChat() }

    /** Собирает чат из строки выборки. */
    private fun Row.toChat(): ChannelChat =
        ChannelChat(
            integrationId = asUuid("integration_id").toChannelIntegrationId(),
            address = asString("address"),
            displayName = asString("display_name"),
            username = asStringOrNull("username"),
            phone = asStringOrNull("phone"),
        )
}
