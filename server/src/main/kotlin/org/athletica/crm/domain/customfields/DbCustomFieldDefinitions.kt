package org.athletica.crm.domain.customfields

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.customfields.CustomFieldDefinition
import org.athletica.crm.core.customfields.CustomFieldType
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asString

/** R2DBC-реализация [CustomFieldDefinitions]. */
class DbCustomFieldDefinitions : CustomFieldDefinitions {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
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
                CustomFieldDefinition(
                    orgId = ctx.orgId,
                    entityType = entityType,
                    fieldKey = row.asString("field_key"),
                    label = row.asString("label"),
                    fieldType =
                        CustomFieldType.parse(
                            row.asString("field_type"),
                            row.asString("config"),
                        ),
                    isRequired = row.asBoolean("is_required"),
                    isSearchable = row.asBoolean("is_searchable"),
                    isSortable = row.asBoolean("is_sortable"),
                )
            }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
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
                .bind("fieldKey", def.fieldKey)
                .bind("label", def.label)
                .bind("fieldType", def.fieldType.typeName())
                .bind("config", def.fieldType.configJson())
                .bind("isRequired", def.isRequired)
                .bind("isSearchable", def.isSearchable)
                .bind("isSortable", def.isSortable)
                .bind("displayOrder", index)
                .execute()
        }
    }
}
