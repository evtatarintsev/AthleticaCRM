package org.athletica.crm.domain.enrollments

import arrow.core.raise.context.Raise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.storage.Transaction

/**
 * Данные о изменении состава группы для записи в журнал аудита.
 * [groupId] — идентификатор группы, [clientIds] — затронутые клиенты.
 */
@Serializable
private data class EnrollmentAuditData(
    val groupId: GroupId,
    val clientIds: List<ClientId>,
)

/**
 * Декоратор [Enrollments], добавляющий запись в журнал аудита.
 * [add] логирует CREATE "enrollment" для каждого добавленного клиента.
 * [remove] логирует DELETE "enrollment" для каждого удалённого клиента.
 */
class AuditEnrollments(private val delegate: Enrollments, private val audit: AuditLog) : Enrollments by delegate {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun add(groupId: GroupId, clientIds: List<ClientId>) {
        delegate.add(groupId, clientIds)
        val data = Json.encodeToString(EnrollmentAuditData(groupId, clientIds))
        clientIds.forEach { clientId ->
            audit.logCreate("enrollment", clientId, data)
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun remove(groupId: GroupId, clientIds: List<ClientId>) {
        delegate.remove(groupId, clientIds)
        val data = Json.encodeToString(EnrollmentAuditData(groupId, clientIds))
        clientIds.forEach { clientId ->
            audit.logDelete("enrollment", clientId, data)
        }
    }
}
