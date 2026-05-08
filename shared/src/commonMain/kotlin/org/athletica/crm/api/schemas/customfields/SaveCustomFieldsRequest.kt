package org.athletica.crm.api.schemas.customfields

import kotlinx.serialization.Serializable
import org.athletica.crm.core.customfields.CustomFieldDefinition

/**
 * Запрос на сохранение набора кастомных полей для одного типа сущности.
 * Заменяет весь набор полей; порядок в [fields] определяет display_order.
 */
@Serializable
data class SaveCustomFieldsRequest(
    /** Тип сущности (например, "CLIENT", "EMPLOYEE"). */
    val entityType: String,
    /** Полный набор определений, который должен быть установлен после операции. */
    val fields: List<CustomFieldDefinition>,
)
