package org.athletica.crm.api.schemas.clients

import kotlinx.serialization.Serializable
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.entityids.ClientContactId

/** Контакт клиента: значение [value] определённого типа [type] (телефон, email, Telegram и т.п.). */
@Serializable
data class ClientContactSchema(
    /** Идентификатор контакта. */
    val id: ClientContactId,
    /** Тип контакта. */
    val type: ContactType,
    /** Значение контакта (номер, адрес, имя пользователя). */
    val value: String,
)

/** Контакт клиента в составе запроса на создание/редактирование клиента (ещё без идентификатора). */
@Serializable
data class ClientContactInput(
    /** Тип контакта. */
    val type: ContactType,
    /** Значение контакта (номер, адрес, имя пользователя). */
    val value: String,
)
