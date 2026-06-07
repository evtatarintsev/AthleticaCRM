package org.athletica.crm.domain.discipline

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/** Декоратор [Disciplines], оборачивающий выдаваемые дисциплины в [AuditDiscipline]. */
class AuditDisciplines(private val delegate: Disciplines, private val audit: AuditLog) : Disciplines by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list() = delegate.list().map { AuditDiscipline(it, audit) }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: DisciplineId, name: String) = AuditDiscipline(delegate.new(id, name), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: DisciplineId) = AuditDiscipline(delegate.byId(id), audit)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<DisciplineId>) = delegate.byIds(ids).map { AuditDiscipline(it, audit) }
}

/** Декоратор [Discipline], логирующий сохранение и удаление. */
class AuditDiscipline(private val delegate: Discipline, private val audit: AuditLog) : Discipline by delegate {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate("discipline", id, Json.encodeToString(DisciplineAuditData(name)))
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() =
        delegate.delete().also {
            audit.logDelete("discipline", id, "")
        }

    override fun withNew(name: String) = AuditDiscipline(delegate.withNew(name), audit)
}

/** Снимок дисциплины для журнала аудита (доменная сущность не сериализуется напрямую). */
@Serializable
private data class DisciplineAuditData(val name: String)
