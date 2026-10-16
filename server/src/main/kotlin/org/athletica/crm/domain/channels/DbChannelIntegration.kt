package org.athletica.crm.domain.channels

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ChannelIntegrationId
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelAddressing
import org.athletica.crm.domain.clientcontacts.ClientContacts
import org.athletica.crm.storage.Transaction

/**
 * Интеграция канала, собранная из сохранённых настроек: адрес разрешается по [addressing] вида канала,
 * отправка делегируется [transport] провайдера. Правило адресации одно на все провайдеры —
 * добавление провайдера не требует повторять его.
 */
class DbChannelIntegration(
    override val id: ChannelIntegrationId,
    override val name: String,
    override val enabled: Boolean,
    private val addressing: ChannelAddressing,
    private val transport: ChannelTransport,
    private val contacts: ClientContacts,
    private val bindings: ChannelBindings,
) : ChannelIntegration {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun recipientAddress(clientId: ClientId, contactId: ClientContactId?): String =
        when (addressing) {
            is ChannelAddressing.ByContact -> contactAddress(clientId, contactId, addressing)
            ChannelAddressing.ByBinding -> bindingAddress(clientId)
        }

    override suspend fun send(address: String, body: String): SendResult = transport.send(address, body)

    /**
     * Адрес прямого канала: контакт [contactId] или первый подходящий по [addressing].
     * `CONTACT_NOT_FOUND` — выбранный контакт не найден или не подходит; `CLIENT_HAS_NO_CONTACT` —
     * подходящих контактов нет.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun contactAddress(
        clientId: ClientId,
        contactId: ClientContactId?,
        addressing: ChannelAddressing.ByContact,
    ): String {
        val candidates = contacts.byClient(clientId).filter { addressing.accepts(it.type) }
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

    /** Адрес связанного канала из связки клиента [clientId]; `CLIENT_NOT_LINKED`, если связки нет. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun bindingAddress(clientId: ClientId): String =
        bindings.addressFor(id, clientId)
            ?: raise(CommonDomainError("CLIENT_NOT_LINKED", "Клиент не привязан к этому каналу"))
}
