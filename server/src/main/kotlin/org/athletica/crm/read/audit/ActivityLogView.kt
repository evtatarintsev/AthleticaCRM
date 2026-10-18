package org.athletica.crm.read.audit

import org.athletica.crm.api.schemas.audit.AuditLogUser
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UserId
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.storage.Transaction
import kotlin.uuid.Uuid

/** Параметры страницы журнала действий. Фильтры применяются до пагинации. */
data class ActivityLogQuery(
    val limit: UInt,
    val offset: UInt,
    val actionType: AuditActionType? = null,
    val userId: UserId? = null,
    val entityType: String? = null,
    val from: String? = null,
    val to: String? = null,
)

/** Запись журнала вместе с разрешённым названием субъекта, если объект ещё существует. */
data class ActivityLogEntry(
    val id: Uuid,
    val orgId: OrgId,
    val userId: UserId?,
    val username: String,
    val actionType: AuditActionType,
    val entityType: String?,
    val entityId: Uuid?,
    val subjectTitle: String?,
    val subjectTargetId: Uuid? = null,
    val data: String?,
    val ipAddress: String?,
    val createdAt: String,
)

/** Read-проекция журнала: фильтры, организация, субъекты и пагинация для экрана аудита. */
interface ActivityLogView {
    /** Возвращает страницу событий в обратном хронологическом порядке. */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    suspend fun list(query: ActivityLogQuery): List<ActivityLogEntry>

    /** Считает все события, соответствующие фильтрам запроса без пагинации. */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    suspend fun count(query: ActivityLogQuery): Long

    /** Уникальные авторы журнала в текущей организации с последним сохранённым именем. */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    suspend fun users(): List<AuditLogUser>
}
