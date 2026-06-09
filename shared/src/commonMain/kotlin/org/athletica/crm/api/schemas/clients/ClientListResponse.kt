package org.athletica.crm.api.schemas.clients

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.core.Gender
import org.athletica.crm.core.contacts.ContactType
import org.athletica.crm.core.customfields.CustomFieldValue
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.money.Money

/** Краткая запись клиента в списке. */
@Serializable
data class ClientListItem(
    /** Уникальный идентификатор клиента. */
    val id: ClientId,
    /** Отображаемое имя клиента. */
    val name: String,
    /** Идентификатор загрузки аватарки клиента, либо null если аватарка не задана. */
    val avatarId: UploadId? = null,
    /** День рождения клиента, либо null если не указан. */
    val birthday: LocalDate? = null,
    /** Пол клиента. */
    val gender: Gender,
    /** Группы в которых состоит клиент. */
    val groups: List<ClientGroup>,
    /** Баланс личного счёта клиента (отрицательный — задолженность). */
    val balance: Money,
    /** Значения кастомных полей клиента. */
    val customFields: List<CustomFieldValue> = emptyList(),
    /** Контакты клиента (телефоны, email и т.п.). */
    val contacts: List<ClientContactSchema> = emptyList(),
    /** Состояние клиента: активный или архивный. */
    val state: ClientState = ClientState.ACTIVE,
)

/** Возвращает значение кастомного поля по его ключу, либо null если поле не найдено. */
fun ClientListItem.field(fieldKey: String): CustomFieldValue? = customFields.firstOrNull { it.fieldKey.value == fieldKey }

/** Возвращает значения контактов заданного типа [type] в порядке добавления. */
fun ClientListItem.contactsOfType(type: ContactType): List<String> = contacts.filter { it.type == type }.map { it.value }

/** Ответ на запрос списка клиентов с поддержкой пагинации. */
@Serializable
data class ClientListResponse(
    /** Клиенты текущей страницы. */
    val clients: List<ClientListItem>,
    /** Общее количество клиентов в организации (без учёта пагинации). */
    val total: UInt,
)
