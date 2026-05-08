package org.athletica.crm.usecases.customfields

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.customfields.CustomFieldDefinitions
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

private val FIELD_KEY_REGEX = Regex("[a-z_]+")

/**
 * Заменяет весь набор полей для [entityType] и возвращает обновлённый список.
 * Валидирует каждый [CustomFieldDefinition.fieldKey] по регулярному выражению `[a-z_]+`.
 */
context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
suspend fun saveCustomFields(
    definitions: CustomFieldDefinitions,
    entityType: String,
    fields: List<CustomFieldDefinition>,
): List<CustomFieldDefinition> {
    fields.forEach { field ->
        if (!field.fieldKey.matches(FIELD_KEY_REGEX)) {
            raise(CommonDomainError("INVALID_FIELD_KEY", Messages.InvalidFieldKey.localize()))
        }
    }
    definitions.saveAll(entityType, fields)
    return definitions.all(entityType)
}
