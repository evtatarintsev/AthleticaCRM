package org.athletica.crm.domain.clientcontacts

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.messaging.ChannelType
import org.athletica.crm.storage.Transaction

/** Контакт клиента в рамках конкретного типа канала (телефон, telegram chat_id, email и т.п.). */
data class ClientContact(
    val id: ClientContactId,
    val clientId: ClientId,
    val channelType: ChannelType,
    val address: String,
)

/** Репозиторий контактов клиентов. Все операции изолированы по организации. */
interface ClientContacts {
    /** Возвращает контакты клиента [clientId] по всем каналам. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byClient(clientId: ClientId): List<ClientContact>

    /**
     * Возвращает адрес клиента [clientId] для канала [channelType] или `null`, если такого контакта нет.
     * Если у клиента несколько адресов одного типа — возвращается первый по дате создания.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun addressFor(clientId: ClientId, channelType: ChannelType): String?

    /** Добавляет контакт клиенту. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun create(contact: ClientContact)

    /** Удаляет контакт [contactId] клиента [clientId]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun delete(clientId: ClientId, contactId: ClientContactId)
}
