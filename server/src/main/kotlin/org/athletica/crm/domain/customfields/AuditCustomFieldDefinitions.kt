package org.athletica.crm.domain.customfields

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import org.athletica.crm.api.appJson
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/**
 * Декоратор [CustomFieldDefinitions], записывающий в журнал аудита каждое добавленное,
 * изменённое и удалённое определение при сохранении набора полей.
 */
class AuditCustomFieldDefinitions(private val delegate: CustomFieldDefinitions, private val audit: AuditLog) : CustomFieldDefinitions by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun saveAll(entityType: String, definitions: List<CustomFieldDefinition>) {
        val before = delegate.stored(entityType).associateBy { it.definition.fieldKey }
        delegate.saveAll(entityType, definitions)
        val after = delegate.stored(entityType)
        after.forEach { current ->
            val previous = before[current.definition.fieldKey]
            when {
                previous == null -> audit.logCreate(AUDIT_ENTITY_TYPE, current.id, current.auditData(entityType))
                previous.definition != current.definition || previous.position != current.position ->
                    audit.logUpdate(AUDIT_ENTITY_TYPE, current.id, current.auditData(entityType))
            }
        }
        val remaining = after.map { it.definition.fieldKey }.toSet()
        before.values.filter { it.definition.fieldKey !in remaining }.forEach {
            audit.logDelete(AUDIT_ENTITY_TYPE, it.id, it.auditData(entityType))
        }
    }

    private companion object {
        const val AUDIT_ENTITY_TYPE = "custom_field"
    }
}

private fun StoredCustomFieldDefinition.auditData(entityType: String): String = appJson.encodeToString(CustomFieldAuditData(entityType, position, definition))

/** Снимок определения поля для журнала аудита. */
@Serializable
private data class CustomFieldAuditData(
    /** Тип сущности, к которой относится поле (например, `CLIENT`). */
    val entityType: String,
    /** Позиция поля в наборе. */
    val position: Int,
    /** Определение поля. */
    val definition: CustomFieldDefinition,
)
