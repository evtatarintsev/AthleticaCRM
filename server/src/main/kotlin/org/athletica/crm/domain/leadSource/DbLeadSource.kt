package org.athletica.crm.domain.leadSource

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** R2DBC-реализация источника привлечения. Все запросы фильтруются по `org_id`. */
data class DbLeadSource(
    override val id: LeadSourceId,
    override val name: String,
) : LeadSource {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        try {
            tr.sql(
                """
                INSERT INTO lead_sources (id, org_id, name)
                VALUES (:id, :orgId, :name)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name
                WHERE lead_sources.org_id = :orgId
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("name", name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("LEAD_SOURCE_ALREADY_EXISTS", Messages.LeadSourceAlreadyExists.localize()))
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() {
        tr.sql("DELETE FROM lead_sources WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    override fun withNew(name: String): LeadSource = copy(name = name)
}
