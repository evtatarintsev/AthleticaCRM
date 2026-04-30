package org.athletica.crm.domain.leadSource

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

class AuditLeadSources(private val delegate: LeadSources, private val audit: AuditLog) : LeadSources by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(leadSource: LeadSource) =
        delegate.create(leadSource).also {
            audit.logCreate("lead_source", leadSource.id, Json.encodeToString(leadSource))
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(leadSource: LeadSource) =
        delegate.update(leadSource).also {
            audit.logUpdate("lead_source", leadSource.id, Json.encodeToString(leadSource))
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(ids: List<LeadSourceId>) =
        delegate.delete(ids).also {
            ids.forEach { id ->
                audit.logDelete("lead_source", id, "")
            }
        }
}
