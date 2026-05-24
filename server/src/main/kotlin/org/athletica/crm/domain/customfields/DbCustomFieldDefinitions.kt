package org.athletica.crm.domain.customfields

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.athletica.crm.api.client.appJson
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString

/** R2DBC-реализация [CustomFieldDefinitions]. */
class DbCustomFieldDefinitions : CustomFieldDefinitions {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun all(entityType: String): List<CustomFieldDefinition> =
        tr.sql(
            """
            SELECT field_key, label, field_type::text, config::text,
                   is_required, is_searchable, is_sortable
            FROM custom_field_definitions
            WHERE org_id = :orgId AND entity_type = :entityType
            ORDER BY display_order
            """.trimIndent(),
        )
            .bind("orgId", ctx.orgId)
            .bind("entityType", entityType)
            .list { row ->
                customFieldDefinition(
                    fieldType = row.asString("field_type"),
                    configJson = row.asString("config"),
                    fieldKey = row.asString("field_key"),
                    label = row.asString("label"),
                    isRequired = row.asBoolean("is_required"),
                    isSearchable = row.asBoolean("is_searchable"),
                    isSortable = row.asBoolean("is_sortable"),
                )
            }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun saveAll(entityType: String, definitions: List<CustomFieldDefinition>) {
        tr.sql(
            "DELETE FROM custom_field_definitions WHERE org_id = :orgId AND entity_type = :entityType",
        )
            .bind("orgId", ctx.orgId)
            .bind("entityType", entityType)
            .execute()

        definitions.forEachIndexed { index, def ->
            tr.sql(
                """
                INSERT INTO custom_field_definitions
                    (org_id, entity_type, field_key, label, field_type, config,
                     is_required, is_searchable, is_sortable, display_order)
                VALUES
                    (:orgId, :entityType, :fieldKey, :label, :fieldType::custom_field_type, :config::jsonb,
                     :isRequired, :isSearchable, :isSortable, :displayOrder)
                """.trimIndent(),
            )
                .bind("orgId", ctx.orgId)
                .bind("entityType", entityType)
                .bind("fieldKey", def.fieldKey.value)
                .bind("label", def.label)
                .bind("fieldType", def.fieldTypeName())
                .bind("config", def.configJson())
                .bind("isRequired", def.isRequired)
                .bind("isSearchable", def.isSearchable)
                .bind("isSortable", def.isSortable)
                .bind("displayOrder", index)
                .execute()
        }
    }
}

private val COMMON_FIELDS = setOf("fieldType", "fieldKey", "label", "isRequired", "isSearchable", "isSortable")

/** Имя типа поля для колонки `field_type`; совпадает с `@SerialName` подтипа. */
private fun CustomFieldDefinition.fieldTypeName(): String =
    when (this) {
        is CustomFieldDefinition.Text -> "text"
        is CustomFieldDefinition.Number -> "number"
        is CustomFieldDefinition.Date -> "date"
        is CustomFieldDefinition.Bool -> "boolean"
        is CustomFieldDefinition.Phone -> "phone"
        is CustomFieldDefinition.Email -> "email"
        is CustomFieldDefinition.Url -> "url"
        is CustomFieldDefinition.Select -> "select"
    }

/**
 * Сериализует только специфичную для подтипа часть определения для колонки `config`.
 * Общие поля (fieldKey, label, флаги) хранятся в отдельных колонках.
 */
private fun CustomFieldDefinition.configJson(): String {
    val full = appJson.encodeToJsonElement<CustomFieldDefinition>(this).jsonObject
    val typeOnly = full.filterKeys { it !in COMMON_FIELDS }
    return JsonObject(typeOnly).toString()
}

/**
 * Восстанавливает полиморфный [CustomFieldDefinition] из строки таблицы:
 * сшивает дискриминатор и общие поля с распарсенным [configJson].
 */
private fun customFieldDefinition(
    fieldType: String,
    configJson: String,
    fieldKey: String,
    label: String,
    isRequired: Boolean,
    isSearchable: Boolean,
    isSortable: Boolean,
): CustomFieldDefinition {
    val config = appJson.parseToJsonElement(configJson).jsonObject
    val full =
        JsonObject(
            config +
                mapOf(
                    "fieldType" to JsonPrimitive(fieldType),
                    "fieldKey" to JsonPrimitive(fieldKey),
                    "label" to JsonPrimitive(label),
                    "isRequired" to JsonPrimitive(isRequired),
                    "isSearchable" to JsonPrimitive(isSearchable),
                    "isSortable" to JsonPrimitive(isSortable),
                ),
        )
    return appJson.decodeFromJsonElement<CustomFieldDefinition>(full)
}
