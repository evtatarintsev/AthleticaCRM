package org.athletica.crm.domain.leadSource

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.entityids.toLeadSourceId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

class DbLeadSources : LeadSources {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<LeadSource> =
        tr.sql("SELECT ls.id, ls.name FROM lead_sources ls WHERE ls.org_id = :orgId ORDER BY ls.name")
            .bind("orgId", ctx.orgId)
            .list {
                LeadSource(
                    id = it.asUuid("id").toLeadSourceId(),
                    name = it.asString("name"),
                )
            }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(leadSource: LeadSource) {
        try {
            tr.sql("INSERT INTO lead_sources (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", leadSource.id)
                .bind("orgId", ctx.orgId)
                .bind("name", leadSource.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("LEAD_SOURCE_ALREADY_EXISTS", Messages.LeadSourceAlreadyExists.localize()))
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(leadSource: LeadSource) {
        val updatedRows =
            try {
                tr.sql("UPDATE lead_sources SET name = :name WHERE id = :id AND org_id = :orgId")
                    .bind("id", leadSource.id)
                    .bind("orgId", ctx.orgId)
                    .bind("name", leadSource.name)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("LEAD_SOURCE_NAME_ALREADY_EXISTS", Messages.LeadSourceAlreadyExists.localize()))
            }

        if (updatedRows == 0L) {
            raise(CommonDomainError("LEAD_SOURCE_NOT_FOUND", Messages.LeadSourceNotFound.localize()))
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(ids: List<LeadSourceId>) {
        if (ids.isEmpty()) return

        tr.sql("DELETE FROM lead_sources WHERE id = ANY(:ids) AND org_id = :orgId RETURNING id, name")
            .bind("ids", ids)
            .bind("orgId", ctx.orgId)
            .list {
                LeadSource(
                    id = it.asUuid("id").toLeadSourceId(),
                    name = it.asString("name"),
                )
            }
    }
}
