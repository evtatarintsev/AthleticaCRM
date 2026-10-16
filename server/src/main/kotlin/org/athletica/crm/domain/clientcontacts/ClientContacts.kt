package org.athletica.crm.domain.clientcontacts

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/**
 * Контакт клиента: значение [value] определённого типа [type] (телефон, email, Telegram и т.п.).
 * Какие каналы умеют адресовать контакт — свойство вида канала
 * ([org.athletica.crm.core.messaging.ChannelConfig.addressing]), а не контакта.
 */
data class ClientContact(
    val id: ClientContactId,
    val clientId: ClientId,
    val type: ContactType,
    val value: String,
)

/** Репозиторий контактов клиентов. Все операции изолированы по организации. */
interface ClientContacts {
    /** Возвращает контакты клиента [clientId] в порядке добавления. */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byClient(clientId: ClientId): List<ClientContact>

    /** Возвращает контакты для набора клиентов [clientIds], сгруппированные по идентификатору клиента. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun byClients(clientIds: List<ClientId>): Map<ClientId, List<ClientContact>>

    /**
     * Клиенты организации, у которых есть телефон, совпадающий с [phone] без учёта форматирования
     * (нормализация как у [PhoneDigits]). Каждый клиент встречается один раз.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun clientsByPhone(phone: PhoneDigits): List<ClientId>

    /** Полностью заменяет набор контактов клиента [clientId] на [contacts]. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun replace(clientId: ClientId, contacts: List<ClientContact>)
}
