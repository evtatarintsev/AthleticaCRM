package org.athletica.crm.routes

import arrow.core.raise.context.Raise
import io.ktor.server.routing.route
import org.athletica.crm.api.schemas.clients.AddClientContactRequest
import org.athletica.crm.api.schemas.clients.ClientContactListResponse
import org.athletica.crm.api.schemas.clients.ClientContactSchema
import org.athletica.crm.api.schemas.clients.ClientContactsListRequest
import org.athletica.crm.api.schemas.clients.DeleteClientContactRequest
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.clientcontacts.ClientContact
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.Transaction

/**
 * Регистрирует маршруты управления контактами клиента по каналам связи.
 * Требует контекстного параметра [Database].
 */
context(db: Database)
fun RouteWithContext.clientContactsRoutes(contacts: ClientContacts) {
    route("/clients/contacts") {
        get<ClientContactsListRequest, ClientContactListResponse>("/list") { request ->
            db.transaction { listResponseFor(contacts, request.clientId) }
        }

        post<AddClientContactRequest, ClientContactListResponse>("/add") { request ->
            db.transaction {
                contacts.create(
                    ClientContact(
                        id = ClientContactId.new(),
                        clientId = request.clientId,
                        channelType = request.channelType,
                        address = request.address,
                    ),
                )
                listResponseFor(contacts, request.clientId)
            }
        }

        post<DeleteClientContactRequest, ClientContactListResponse>("/delete") { request ->
            db.transaction {
                contacts.delete(request.clientId, request.contactId)
                listResponseFor(contacts, request.clientId)
            }
        }
    }
}

context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
private suspend fun listResponseFor(
    contacts: ClientContacts,
    clientId: ClientId,
): ClientContactListResponse =
    ClientContactListResponse(
        contacts = contacts.byClient(clientId).map { it.toSchema() },
    )

private fun ClientContact.toSchema(): ClientContactSchema =
    ClientContactSchema(
        id = id,
        channelType = channelType,
        address = address,
    )
