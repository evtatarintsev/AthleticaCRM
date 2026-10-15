package org.athletica.infra.messaging

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.channels.ChannelBindings
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.storage.Transaction

/**
 * Разрешает адрес прямого канала из контакта клиента: берёт контакт [contactId] (или первый
 * подходящий по [accepted]). `CONTACT_NOT_FOUND` — выбранный контакт не найден/не подходит;
 * `CLIENT_HAS_NO_CONTACT` — подходящих контактов нет.
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun contactAddress(
    clientId: ClientId,
    contactId: ClientContactId?,
    accepted: Set<ContactType>,
    contacts: ClientContacts,
): String {
    val candidates = contacts.byClient(clientId).filter { it.type in accepted }
    val chosen =
        contactId
            ?.let { id ->
                candidates.firstOrNull { it.id == id }
                    ?: raise(CommonDomainError("CONTACT_NOT_FOUND", "Контакт не найден или не подходит для канала"))
            }
            ?: candidates.firstOrNull()
            ?: raise(CommonDomainError("CLIENT_HAS_NO_CONTACT", "У клиента нет контакта для этого канала"))
    return chosen.value
}

/**
 * Разрешает адрес связанного канала из связки клиента с интеграцией.
 * `CLIENT_NOT_LINKED` — клиент ещё не привязан к этому каналу (нет связки адреса).
 */
context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun bindingAddress(
    integrationId: ChannelIntegrationId,
    clientId: ClientId,
    bindings: ChannelBindings,
): String =
    bindings.addressFor(integrationId, clientId)
        ?: raise(CommonDomainError("CLIENT_NOT_LINKED", "Клиент не привязан к этому каналу"))
