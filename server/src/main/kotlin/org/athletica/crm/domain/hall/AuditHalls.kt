package org.athletica.crm.domain.hall

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/** Декоратор [Halls], оборачивающий выдаваемые залы в [AuditHall]. */
class AuditHalls(private val delegate: Halls, private val audit: AuditLog) : Halls by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list() = delegate.list().map { AuditHall(it, audit) }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: HallId, name: String) = AuditHall(delegate.new(id, name), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: HallId) = AuditHall(delegate.byId(id), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<HallId>) = delegate.byIds(ids).map { AuditHall(it, audit) }
}

/** Декоратор [Hall], логирующий сохранение и удаление. */
class AuditHall(private val delegate: Hall, private val audit: AuditLog) : Hall by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate("hall", id, Json.encodeToString(HallAuditData(name)))
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() =
        delegate.delete().also {
            audit.logDelete("hall", id, "")
        }

    override fun withNew(name: String) = AuditHall(delegate.withNew(name), audit)
}

/** Снимок зала для журнала аудита (доменная сущность не сериализуется напрямую). */
@Serializable
private data class HallAuditData(val name: String)
