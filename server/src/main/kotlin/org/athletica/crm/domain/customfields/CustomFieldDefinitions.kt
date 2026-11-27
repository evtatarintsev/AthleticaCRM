package org.athletica.crm.domain.customfields

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.entityids.CustomFieldDefinitionId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

internal const val CLIENT_CUSTOM_FIELD_ENTITY_TYPE = "CLIENT"

/** Репозиторий определений кастомных полей организации. */
interface CustomFieldDefinitions {
    /**
     * Возвращает все определения для [entityType] в порядке display_order.
     * Фильтрует по orgId из [RequestContext].
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun all(entityType: String): List<CustomFieldDefinition>

    /** Сохранённые определения для [entityType] вместе с идентификатором и позицией, в порядке display_order. */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun stored(entityType: String): List<StoredCustomFieldDefinition>

    /**
     * Делает [definitions] текущим набором полей для [entityType]: поля с отсутствующими ключами удаляются,
     * остальные создаются или обновляются с сохранением идентификатора.
     * Порядок элементов в [definitions] определяет итоговый display_order.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    suspend fun saveAll(entityType: String, definitions: List<CustomFieldDefinition>)
}

/** Сохранённое определение поля. */
data class StoredCustomFieldDefinition(
    /** Идентификатор строки определения. */
    val id: CustomFieldDefinitionId,
    /** Позиция в наборе (display_order). */
    val position: Int,
    /** Само определение. */
    val definition: CustomFieldDefinition,
)
