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
    /** Свободный комментарий, например «мама»; `null` — без комментария. */
    val label: String?,
    /** Основной контакт своего типа. */
    val isPrimary: Boolean,
)

/** Контакт клиента в составе запроса на создание/редактирование клиента (ещё без идентификатора). */
@Serializable
data class ClientContactInput(
    /** Тип контакта. */
    val type: ContactType,
    /** Значение контакта (номер, адрес, имя пользователя). */
    val value: String,
    /** Свободный комментарий, например «мама»; пустой — без комментария. */
    val label: String? = null,
    /** Отметка «основной»; если в типе не отмечен ни один, основным станет первый. */
    val isPrimary: Boolean = false,
)
