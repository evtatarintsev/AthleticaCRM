package org.athletica.crm.domain.hall

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

class AuditHalls(private val delegate: Halls, private val audit: AuditLog) : Halls by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(hall: Hall) =
        delegate.create(hall).also {
            audit.logCreate("hall", hall.id, Json.encodeToString(hall))
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(hall: Hall) =
        delegate.update(hall).also {
            audit.logUpdate("hall", hall.id, Json.encodeToString(hall))
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(ids: List<HallId>) =
        delegate.delete(ids).also {
            ids.forEach { id ->
                audit.logDelete("hall", id, "")
            }
        }
}
