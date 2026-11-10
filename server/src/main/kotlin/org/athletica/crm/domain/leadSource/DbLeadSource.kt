package org.athletica.crm.domain.leadSource

import arrow.core.raise.context.Raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** R2DBC-реализация источника привлечения. Все запросы фильтруются по `org_id`. */
data class DbLeadSource(
    override val id: LeadSourceId,
    override val name: String,
    override val description: LeadSourceDescription,
) : LeadSource {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun save() {
        tr.sql(
            """
            INSERT INTO lead_sources (id, org_id, name, description)
            VALUES (:id, :orgId, :name, :description)
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description
            WHERE lead_sources.org_id = :orgId
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("name", name)
            .bind("description", description.value)
            .onViolation("lead_sources_org_name_unique") { CommonDomainError("LEAD_SOURCE_ALREADY_EXISTS", Messages.LeadSourceAlreadyExists.localize()) }
            .execute()
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun delete() {
        tr.sql("DELETE FROM lead_sources WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    override fun withNew(name: String, description: LeadSourceDescription): LeadSource = copy(name = name, description = description)
}
