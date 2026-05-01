package org.athletica.crm.core.customfields

import org.athletica.crm.core.entityids.OrgId

/**
 * Определение произвольного поля организации.
 * Набор определений идентифицируется парой ([orgId], [entityType]).
 * Порядок в списке определяет display_order при сохранении.
 */
data class CustomFieldDefinition(
    /** Организация-владелец. */
    val orgId: OrgId,
    /** Тип сущности: "client", "employee" и т.д. */
    val entityType: String,
    /**
     * Иммутабельный машинный ключ.
     * Служит ключом в JSONB при хранении значений сущности
     * (custom_data = {"age_group": "adult"}). Отделён от [label]:
     * переименование отображаемого названия не ломает данные.
     */
    val fieldKey: String,
    /** Отображаемое название поля. */
    val label: String,
    /** Тип и конфигурация поля. */
    val fieldType: CustomFieldType,
    /** Поле обязательно для заполнения. */
    val isRequired: Boolean,
    /** Поле участвует в поиске. */
    val isSearchable: Boolean,
    /** Поле доступно для сортировки. */
    val isSortable: Boolean,
)
