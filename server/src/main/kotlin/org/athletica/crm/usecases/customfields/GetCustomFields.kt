package org.athletica.crm.usecases.customfields

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionSchema
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldType
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.storage.Transaction

/**
 * Возвращает все определения кастомных полей для [entityType] организации из контекста.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun getCustomFields(
    definitions: CustomFieldDefinitions,
    entityType: String,
): List<CustomFieldDefinitionSchema> = definitions.all(entityType).map { it.toSchema() }

/** Конвертирует доменное определение в схему для передачи по API. */
fun CustomFieldDefinition.toSchema(): CustomFieldDefinitionSchema =
    CustomFieldDefinitionSchema(
        fieldKey = fieldKey,
        label = label,
        fieldType = fieldType.name,
        config = fieldType.configJsonObject(),
        isRequired = isRequired,
        isSearchable = isSearchable,
        isSortable = isSortable,
    )

private fun CustomFieldType.configJsonObject(): JsonObject = runCatching { Json.parseToJsonElement(configJson()).jsonObject }.getOrElse { JsonObject(emptyMap()) }
