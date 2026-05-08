package org.athletica.crm.usecases.customfields

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
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
): List<CustomFieldDefinition> = definitions.all(entityType)
