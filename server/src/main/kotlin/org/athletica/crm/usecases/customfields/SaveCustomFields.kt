package org.athletica.crm.usecases.customfields

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.athletica.crm.api.schemas.customfields.CustomFieldDefinitionSchema
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldType
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

private val FIELD_KEY_REGEX = Regex("[a-z_]+")

/**
 * Заменяет весь набор полей для [entityType] и возвращает обновлённый список.
 * Валидирует каждый [field_key][CustomFieldDefinitionSchema.fieldKey] по регулярному выражению `[a-z_]+`.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun saveCustomFields(
    definitions: CustomFieldDefinitions,
    entityType: String,
    fields: List<CustomFieldDefinitionSchema>,
): List<CustomFieldDefinitionSchema> {
    fields.forEach { field ->
        if (!field.fieldKey.matches(FIELD_KEY_REGEX)) {
            raise(CommonDomainError("INVALID_FIELD_KEY", Messages.InvalidFieldKey.localize()))
        }
    }
    val domainDefs = fields.map { it.toDomain(ctx, entityType) }
    definitions.saveAll(entityType, domainDefs)
    return definitions.all(entityType).map { it.toSchema() }
}

/** Конвертирует схему в доменное определение. */
fun CustomFieldDefinitionSchema.toDomain(
    ctx: RequestContext,
    entityType: String,
): CustomFieldDefinition {
    val fieldTypeObj =
        when (fieldType) {
            "text" -> CustomFieldType.Text
            "number" -> CustomFieldType.Number
            "date" -> CustomFieldType.Date
            "boolean" -> CustomFieldType.Boolean
            "phone" -> CustomFieldType.Phone
            "email" -> CustomFieldType.Email
            "url" -> CustomFieldType.Url
            "select" -> {
                val options =
                    config["options"]
                        ?.jsonArray
                        ?.map { it.jsonPrimitive.content }
                        ?: emptyList()
                CustomFieldType.Select(options)
            }
            else -> CustomFieldType.Text
        }
    return CustomFieldDefinition(
        orgId = ctx.orgId,
        entityType = entityType,
        fieldKey = fieldKey,
        label = label,
        fieldType = fieldTypeObj,
        isRequired = isRequired,
        isSearchable = isSearchable,
        isSortable = isSortable,
    )
}
