package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.entityids.ClientContactId
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.messaging.ChannelType

/** Контакт клиента в рамках типа канала (телефон, telegram chat_id, email и т.п.). */
@Serializable
data class ClientContactSchema(
    val id: ClientContactId,
    val channelType: ChannelType,
    val address: String,
)

/** Список контактов клиента. */
@Serializable
data class ClientContactListResponse(
    val contacts: List<ClientContactSchema>,
)

/** Запрос списка контактов клиента. */
@Serializable
data class ClientContactsListRequest(
    val clientId: ClientId,
)

/** Запрос на добавление контакта клиенту. */
@Serializable
data class AddClientContactRequest(
    val clientId: ClientId,
    val channelType: ChannelType,
    val address: String,
)

/** Запрос на удаление контакта клиента. */
@Serializable
data class DeleteClientContactRequest(
    val clientId: ClientId,
    val contactId: ClientContactId,
)
