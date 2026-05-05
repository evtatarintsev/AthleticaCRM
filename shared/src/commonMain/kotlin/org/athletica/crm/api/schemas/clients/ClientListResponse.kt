package org.athletica.crm.api.schemas.clients

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import org.athletica.crm.api.schemas.customfields.CustomFieldValue
import org.athletica.crm.core.Gender
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.UploadId

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
    val balance: Double,
    /** Значения кастомных полей клиента. */
    val customFields: List<CustomFieldValue> = emptyList(),
)

/** Возвращает значение кастомного поля по его ключу, либо null если поле не найдено. */
fun ClientListItem.field(fieldKey: String): CustomFieldValue? = customFields.firstOrNull { it.fieldKey == fieldKey }

/** Ответ на запрос списка клиентов с поддержкой пагинации. */
@Serializable
data class ClientListResponse(
    /** Клиенты текущей страницы. */
    val clients: List<ClientListItem>,
    /** Общее количество клиентов в организации (без учёта пагинации). */
    val total: UInt,
)
