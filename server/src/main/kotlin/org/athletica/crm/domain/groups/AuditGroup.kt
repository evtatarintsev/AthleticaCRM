package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.storage.Transaction

/**
 * Снапшот группы для записи в журнал аудита.
 * Содержит все поля группы в сериализуемом виде.
 */
@Serializable
private data class GroupSnapshot(
    val name: String,
    val schedule: List<ScheduleSlot>,
    val disciplines: List<DisciplineId>,
    val employeeIds: List<EmployeeId>,
)

private fun Group.snapshot() = Json.encodeToString(GroupSnapshot(name, schedule, disciplines, employeeIds))

/**
 * Декоратор [Group], добавляющий запись в журнал аудита при мутирующих операциях.
 * [save] логирует событие UPDATE "group".
 * [withNew] возвращает новый [AuditGroup], сохраняя аудит-поведение в цепочках.
 */
class AuditGroup(private val delegate: Group, private val audit: AuditLog) : Group by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() =
        delegate.save().also {
            audit.logUpdate("group", id, delegate.snapshot())
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun withNewSchedule(schedule: List<ScheduleSlot>): Group = AuditGroup(delegate.withNewSchedule(schedule), audit)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun withNewDisciplines(disciplines: List<DisciplineId>): Group = AuditGroup(delegate.withNewDisciplines(disciplines), audit)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun withNewEmployees(employeeIds: List<EmployeeId>): Group = AuditGroup(delegate.withNewEmployees(employeeIds), audit)

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun withNewName(name: String): Group = AuditGroup(delegate.withNewName(name), audit)
}
