package org.athletica.crm.domain.leadSource

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.LeadSourceId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/** Декоратор [LeadSources], оборачивающий выдаваемые источники в [AuditLeadSource]. */
class AuditLeadSources(private val delegate: LeadSources, private val audit: AuditLog) : LeadSources by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list() = delegate.list().map { AuditLeadSource(it, audit) }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: LeadSourceId, name: String) = AuditLeadSource(delegate.new(id, name), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: LeadSourceId) = AuditLeadSource(delegate.byId(id), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<LeadSourceId>) = delegate.byIds(ids).map { AuditLeadSource(it, audit) }
}

/** Декоратор [LeadSource], логирующий сохранение и удаление. */
class AuditLeadSource(private val delegate: LeadSource, private val audit: AuditLog) : LeadSource by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate("lead_source", id, Json.encodeToString(LeadSourceAuditData(name)))
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() =
        delegate.delete().also {
            audit.logDelete("lead_source", id, "")
        }

    override fun withNew(name: String) = AuditLeadSource(delegate.withNew(name), audit)
}

/** Снимок источника для журнала аудита (доменная сущность не сериализуется напрямую). */
@Serializable
private data class LeadSourceAuditData(val name: String)
