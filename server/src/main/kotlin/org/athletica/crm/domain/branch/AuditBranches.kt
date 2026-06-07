package org.athletica.crm.domain.branch

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/** Декоратор [Branches], оборачивающий выдаваемые филиалы в [AuditBranch]. */
class AuditBranches(private val delegate: Branches, private val audit: AuditLog) : Branches by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction)
    override suspend fun list() = delegate.list().map { AuditBranch(it, audit) }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: BranchId, name: String) = AuditBranch(delegate.new(id, name), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: BranchId) = AuditBranch(delegate.byId(id), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<BranchId>) = delegate.byIds(ids).map { AuditBranch(it, audit) }
}

/** Декоратор [Branch], логирующий сохранение и удаление. */
class AuditBranch(private val delegate: Branch, private val audit: AuditLog) : Branch by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate("branch", id, Json.encodeToString(BranchAuditData(name)))
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() =
        delegate.delete().also {
            audit.logDelete("branch", id, "")
        }

    override fun withNew(name: String) = AuditBranch(delegate.withNew(name), audit)
}

/** Снимок филиала для журнала аудита (доменная сущность не сериализуется напрямую). */
@Serializable
private data class BranchAuditData(val name: String)
