package org.athletica.crm.domain.audit

import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

class PostgresAuditLog : AuditLog {
    context(tr: Transaction)
    override suspend fun log(event: AuditEvent) {
        tr.sql(
            """
            INSERT INTO audit_logs (org_id, user_id, username, action_type, entity_type, entity_id, data, ip_address)
            VALUES (:orgId, :userId, :username, :actionType, :entityType, :entityId, :data::jsonb, :ipAddress)
            """.trimIndent(),
        )
            .bind("orgId", event.orgId)
            .bind("userId", event.userId)
            .bind("username", event.username)
            .bind("actionType", event.actionType.code)
            .bind("entityType", event.entityType)
            .bind("entityId", event.entityId)
            .bind("data", event.data)
            .bind("ipAddress", event.ipAddress)
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction)
    override suspend fun list(filter: AuditFilter): List<AuditEvent> =
        baseQuery(filter, "id, user_id, username, action_type, entity_type, entity_id, data::text, ip_address, created_at::text")
            .bind("limit", filter.limit.toLong())
            .bind("offset", filter.offset.toLong())
            .list { row ->
                AuditEvent(
                    orgId = ctx.orgId,
                    userId = row.asUuidOrNull("user_id")?.toUserId(),
                    username = row.asString("username"),
                    actionType = AuditActionType.entries.first { it.code == row.asString("action_type") },
                    entityType = row.asStringOrNull("entity_type"),
                    entityId = row.asUuidOrNull("entity_id"),
                    data = row.asStringOrNull("data"),
                    ipAddress = row.asStringOrNull("ip_address"),
                    id = row.asUuid("id"),
                    createdAt = row.asString("created_at"),
                )
            }

    context(ctx: RequestContext, tr: Transaction)
    override suspend fun count(filter: AuditFilter): Long =
        baseQuery(filter, "COUNT(*) AS cnt", paginate = false)
            .firstOrNull { row -> row.asLong("cnt") } ?: 0L

    /**
     * Строит базовый запрос с динамическим WHERE по полям [filter] и привязывает orgId из [ctx].
     * Если [paginate] = true — добавляет `LIMIT :limit OFFSET :offset` (значения нужно привязать отдельно).
     */
    context(ctx: RequestContext, tr: Transaction)
    private fun baseQuery(filter: AuditFilter, select: String, paginate: Boolean = true): QueryBuilder {
        val conditions = mutableListOf("org_id = :orgId")
        if (filter.actionType != null) conditions += "action_type = :actionType"
        if (filter.userId != null) conditions += "user_id = :userId::uuid"
        if (filter.entityType != null) conditions += "entity_type = :entityType"
        if (filter.from != null) conditions += "created_at >= :from::timestamptz"
        if (filter.to != null) conditions += "created_at <= :to::timestamptz"

        val pagination = if (paginate) " ORDER BY created_at DESC LIMIT :limit OFFSET :offset" else ""

        return tr.sql("SELECT $select FROM audit_logs WHERE ${conditions.joinToString(" AND ")}$pagination")
            .bind("orgId", ctx.orgId)
            .let { q -> if (filter.actionType != null) q.bind("actionType", filter.actionType.code) else q }
            .let { q -> if (filter.userId != null) q.bind("userId", filter.userId) else q }
            .let { q -> if (filter.entityType != null) q.bind("entityType", filter.entityType) else q }
            .let { q -> if (filter.from != null) q.bind("from", filter.from) else q }
            .let { q -> if (filter.to != null) q.bind("to", filter.to) else q }
    }
}
