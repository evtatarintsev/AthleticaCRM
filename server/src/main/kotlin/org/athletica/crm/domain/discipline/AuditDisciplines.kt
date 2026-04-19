package org.athletica.crm.domain.discipline

import arrow.core.raise.context.Raise
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

class AuditDisciplines(private val delegate: Disciplines, private val audit: AuditLog) : Disciplines by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(discipline: Discipline) =
        delegate.create(discipline).also {
            audit.logCreate("discipline", discipline.id, Json.encodeToString(discipline))
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(discipline: Discipline) =
        delegate.update(discipline).also {
            audit.logUpdate("discipline", discipline.id, Json.encodeToString(discipline))
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(ids: List<DisciplineId>) =
        delegate.delete(ids).also {
            ids.forEach { id ->
                audit.logDelete("discipline", id, "")
            }
        }
}
