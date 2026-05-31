package org.athletica.crm.domain.clientcontacts

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.toClientContactId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация репозитория контактов клиентов на PostgreSQL через R2DBC. */
class DbClientContacts : ClientContacts {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byClient(clientId: ClientId): List<ClientContact> =
        tr.sql(
            """
            SELECT id, client_id, channel_type, address
            FROM client_contacts
            WHERE client_id = :clientId AND org_id = :orgId
            ORDER BY created_at
            """.trimIndent(),
        )
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .list { row -> row.toClientContact() }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun addressFor(clientId: ClientId, channelType: ChannelType): String? =
        tr.sql(
            """
            SELECT address
            FROM client_contacts
            WHERE client_id = :clientId AND org_id = :orgId AND channel_type = :channelType
            ORDER BY created_at
            LIMIT 1
            """.trimIndent(),
        )
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .bind("channelType", channelType.name)
            .firstOrNull { row -> row.asString("address") }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(contact: ClientContact) {
        try {
            tr.sql(
                """
                INSERT INTO client_contacts (id, org_id, client_id, channel_type, address)
                VALUES (:id, :orgId, :clientId, :channelType, :address)
                """.trimIndent(),
            )
                .bind("id", contact.id)
                .bind("orgId", ctx.orgId)
                .bind("clientId", contact.clientId)
                .bind("channelType", contact.channelType.name)
                .bind("address", contact.address)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("CLIENT_CONTACT_ALREADY_EXISTS", "Такой контакт у клиента уже есть"))
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(clientId: ClientId, contactId: ClientContactId) {
        tr.sql("DELETE FROM client_contacts WHERE id = :id AND client_id = :clientId AND org_id = :orgId")
            .bind("id", contactId)
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    private fun Row.toClientContact(): ClientContact =
        ClientContact(
            id = asUuid("id").toClientContactId(),
            clientId = asUuid("client_id").toClientId(),
            channelType = ChannelType.valueOf(asString("channel_type")),
            address = asString("address"),
        )
}
