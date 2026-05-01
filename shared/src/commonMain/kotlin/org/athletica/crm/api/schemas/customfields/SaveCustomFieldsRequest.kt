package org.athletica.crm.api.schemas.customfields

import kotlinx.serialization.Serializable

/**
 * Запрос на сохранение набора кастомных полей для одного типа сущности.
 * Заменяет весь набор полей; порядок в [fields] определяет display_order.
 */
@Serializable
data class SaveCustomFieldsRequest(
    val fields: List<CustomFieldDefinitionSchema>,
)
