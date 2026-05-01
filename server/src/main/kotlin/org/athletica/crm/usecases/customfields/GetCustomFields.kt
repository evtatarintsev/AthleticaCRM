package org.athletica.crm.usecases.customfields

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionDto
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
): List<CustomFieldDefinitionDto> = definitions.all(entityType).map { it.toDto() }

/** Конвертирует доменное определение в DTO для передачи по API. */
fun CustomFieldDefinition.toDto(): CustomFieldDefinitionDto =
    CustomFieldDefinitionDto(
        fieldKey = fieldKey,
        label = label,
        fieldType = fieldType.typeName(),
        config = fieldType.configJsonObject(),
        isRequired = isRequired,
        isSearchable = isSearchable,
        isSortable = isSortable,
    )

private fun CustomFieldType.configJsonObject(): JsonObject = runCatching { Json.parseToJsonElement(configJson()).jsonObject }.getOrElse { JsonObject(emptyMap()) }
