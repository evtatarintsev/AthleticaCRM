package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.storage.Transaction

/**
 * Снапшот новой группы для записи события CREATE в журнал аудита.
 * Содержит все поля, переданные при создании.
 */
@Serializable
private data class NewGroupSnapshot(
    val id: GroupId,
    val name: String,
    val schedule: List<ScheduleSlot>,
    val disciplineIds: List<DisciplineId>,
    val employeeIds: List<EmployeeId>,
)

/**
 * Декоратор [Groups], добавляющий запись в журнал аудита при создании и получении группы.
 * [new] логирует событие CREATE "group".
 * [byId] оборачивает результат в [AuditGroup], сохраняя аудит-поведение при дальнейших мутациях.
 */
class AuditGroups(private val delegate: Groups, private val audit: AuditLog) : Groups by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: GroupId,
        name: String,
        schedule: List<ScheduleSlot>,
        disciplineIds: List<DisciplineId>,
        employeeIds: List<EmployeeId>,
    ): Group =
        delegate.new(id, name, schedule, disciplineIds, employeeIds).also {
            audit.logCreate("group", id, Json.encodeToString(NewGroupSnapshot(id, name, schedule, disciplineIds, employeeIds)))
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: GroupId): Group = AuditGroup(delegate.byId(id), audit)
}
