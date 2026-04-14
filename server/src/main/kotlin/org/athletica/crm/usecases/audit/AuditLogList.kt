package org.athletica.crm.usecases.audit

import arrow.core.Either
import arrow.core.right
import org.athletica.crm.api.schemas.audit.AuditLogItem
import org.athletica.crm.api.schemas.audit.AuditLogListResponse
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.db.asLong
import org.athletica.crm.db.asString
import org.athletica.crm.db.asStringOrNull
import org.athletica.crm.db.asUuid
import org.athletica.crm.db.asUuidOrNull

data class AuditLogListRequest(
    val page: Int = 0,
    val pageSize: Int = 50,
    val actionType: String? = null,
    val userId: String? = null,
    val entityType: String? = null,
    val from: String? = null,
    val to: String? = null,
)

/**
 * Возвращает страницу лога аудита для организации из [ctx].
 * Поддерживает фильтрацию по типу действия, пользователю, типу сущности и диапазону дат.
 */
context(db: Database, ctx: RequestContext)
suspend fun auditLogList(request: AuditLogListRequest): Either<CommonDomainError, AuditLogListResponse> {
    val conditions = mutableListOf("org_id = :orgId")
    if (request.actionType != null) {
        conditions += "action_type = :actionType"
    }
    if (request.userId != null) {
        conditions += "user_id = :userId::uuid"
    }
    if (request.entityType != null) {
        conditions += "entity_type = :entityType"
    }
    if (request.from != null) {
        conditions += "created_at >= :from::timestamptz"
    }
    if (request.to != null) {
        conditions += "created_at <= :to::timestamptz"
    }

    val whereClause = conditions.joinToString(" AND ")
    val offset = request.page * request.pageSize

    fun buildQuery(select: String) =
        db.sql("SELECT $select FROM audit_logs WHERE $whereClause ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
            .bind("orgId", ctx.orgId)
            .let { q -> if (request.actionType != null) q.bind("actionType", request.actionType) else q }
            .let { q -> if (request.userId != null) q.bind("userId", request.userId) else q }
            .let { q -> if (request.entityType != null) q.bind("entityType", request.entityType) else q }
            .let { q -> if (request.from != null) q.bind("from", request.from) else q }
            .let { q -> if (request.to != null) q.bind("to", request.to) else q }
            .bind("limit", request.pageSize)
            .bind("offset", offset)

    val items =
        buildQuery("id, user_id, username, action_type, entity_type, entity_id, data::text, ip_address, created_at").list { row ->
            AuditLogItem(
                id = row.asUuid("id"),
                userId = row.asUuid("userId"),
                username = row.asString("username"),
                actionType = row.asString("action_type"),
                entityType = row.asStringOrNull("entity_type"),
                entityId = row.asUuidOrNull("entity_id"),
                data = row.asStringOrNull("data"),
                ipAddress = row.asStringOrNull("ip_address"),
                createdAt = row.asString("created_at"),
            )
        }

    val total =
        db.sql("SELECT COUNT(*) as cnt FROM audit_logs WHERE $whereClause")
            .bind("orgId", ctx.orgId)
            .let { q -> if (request.actionType != null) q.bind("actionType", request.actionType) else q }
            .let { q -> if (request.userId != null) q.bind("userId", request.userId) else q }
            .let { q -> if (request.entityType != null) q.bind("entityType", request.entityType) else q }
            .let { q -> if (request.from != null) q.bind("from", request.from) else q }
            .let { q -> if (request.to != null) q.bind("to", request.to) else q }
            .firstOrNull { row -> row.asLong("cnt") } ?: 0L

    return AuditLogListResponse(
        items = items,
        total = total,
        page = request.page,
        pageSize = request.pageSize,
    ).right()
}
