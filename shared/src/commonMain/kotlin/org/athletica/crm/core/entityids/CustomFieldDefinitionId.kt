package org.athletica.crm.core.entityids

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/**
 * Идентификатор определения дополнительного поля.
 * Сохраняется между сохранениями набора полей, пока поле с тем же ключом остаётся в наборе.
 */
@Serializable
@JvmInline
value class CustomFieldDefinitionId(override val value: Uuid) : EntityId {
    override fun toString() = value.toString()
}

fun Uuid.toCustomFieldDefinitionId(): CustomFieldDefinitionId = CustomFieldDefinitionId(this)
