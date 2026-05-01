package org.athletica.crm.domain.customfields

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction

/** Репозиторий определений кастомных полей организации. */
interface CustomFieldDefinitions {
    /**
     * Возвращает все определения для [entityType] в порядке display_order.
     * Фильтрует по orgId из [RequestContext].
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun all(entityType: String): List<CustomFieldDefinition>

    /**
     * Заменяет весь набор полей для [entityType] за одну операцию (DELETE + INSERT).
     * Порядок элементов в [definitions] определяет итоговый display_order.
     */
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    suspend fun saveAll(entityType: String, definitions: List<CustomFieldDefinition>)
}
