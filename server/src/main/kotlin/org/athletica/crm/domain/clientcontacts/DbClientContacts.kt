package org.athletica.crm.domain.clientcontacts

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.toClientContactId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация репозитория контактов клиентов на PostgreSQL через R2DBC. */
class DbClientContacts : ClientContacts {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byClient(clientId: ClientId): List<ClientContact> =
        tr.sql(
            """
            SELECT id, client_id, type, value
            FROM client_contacts
            WHERE client_id = :clientId AND org_id = :orgId
            ORDER BY created_at
            """.trimIndent(),
        )
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .list { row -> row.toClientContact() }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byClients(clientIds: List<ClientId>): Map<ClientId, List<ClientContact>> {
        if (clientIds.isEmpty()) {
            return emptyMap()
        }

        return tr.sql(
            """
            SELECT id, client_id, type, value
            FROM client_contacts
            WHERE org_id = :orgId AND client_id = ANY(:clientIds)
            ORDER BY created_at
            """.trimIndent(),
        )
            .bind("orgId", ctx.orgId)
            .bind("clientIds", clientIds.map { it.value })
            .list { row -> row.toClientContact() }
            .groupBy { it.clientId }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun replace(clientId: ClientId, contacts: List<ClientContact>) {
        tr.sql("DELETE FROM client_contacts WHERE client_id = :clientId AND org_id = :orgId")
            .bind("clientId", clientId)
            .bind("orgId", ctx.orgId)
            .execute()

        contacts.forEach { contact ->
            tr.sql(
                """
                INSERT INTO client_contacts (id, org_id, client_id, type, value)
                VALUES (:id, :orgId, :clientId, :type, :value)
                """.trimIndent(),
            )
                .bind("id", contact.id)
                .bind("orgId", ctx.orgId)
                .bind("clientId", contact.clientId)
                .bind("type", contact.type.name)
                .bind("value", contact.value)
                .execute()
        }
    }

    private fun Row.toClientContact(): ClientContact =
        ClientContact(
            id = asUuid("id").toClientContactId(),
            clientId = asUuid("client_id").toClientId(),
            type = ContactType.valueOf(asString("type")),
            value = asString("value"),
        )
}
