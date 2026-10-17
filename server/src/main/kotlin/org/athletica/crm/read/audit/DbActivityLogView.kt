package org.athletica.crm.read.audit

import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.audit.AuditLogUser
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toOrgId
import org.athletica.crm.core.entityids.toUserId
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/** PostgreSQL-реализация [ActivityLogView]. */
class DbActivityLogView : ActivityLogView {
    context(ctx: EmployeeRequestContext, tr: Transaction)
    override suspend fun list(query: ActivityLogQuery): List<ActivityLogEntry> =
        tr
            .sql("$SELECT_SQL\n${whereSql(query)}\nORDER BY a.created_at DESC, a.id DESC\nLIMIT :limit OFFSET :offset")
            .bindFilters(query)
            .bind("limit", query.limit.toLong())
            .bind("offset", query.offset.toLong())
            .list { row -> row.toEntry() }

    context(ctx: EmployeeRequestContext, tr: Transaction)
    override suspend fun count(query: ActivityLogQuery): Long =
        tr
            .sql("SELECT COUNT(*) AS total FROM audit_logs a\n${whereSql(query)}")
            .bindFilters(query)
            .firstOrNull { row -> row.asLong("total") }
            ?: 0L

    context(ctx: EmployeeRequestContext, tr: Transaction)
    override suspend fun users(): List<AuditLogUser> =
        tr.sql(
            """
            SELECT DISTINCT ON (user_id) user_id, username
            FROM audit_logs
            WHERE org_id = :orgId AND user_id IS NOT NULL
            ORDER BY user_id, created_at DESC, id DESC
            """.trimIndent(),
        )
            .bind("orgId", ctx.orgId)
            .list { row -> AuditLogUser(row.asUuid("user_id").toUserId(), row.asString("username")) }

    context(ctx: EmployeeRequestContext)
    private fun QueryBuilder.bindFilters(query: ActivityLogQuery): QueryBuilder =
        bind("orgId", ctx.orgId)
            .let { q -> if (query.actionType != null) q.bind("actionType", query.actionType.code) else q }
            .let { q -> if (query.userId != null) q.bind("userId", query.userId) else q }
            .let { q -> if (query.entityType != null) q.bind("entityType", query.entityType) else q }
            .let { q -> if (query.from != null) q.bind("from", query.from) else q }
            .let { q -> if (query.to != null) q.bind("to", query.to) else q }

    private fun whereSql(query: ActivityLogQuery): String =
        buildList {
            add("a.org_id = :orgId")
            if (query.actionType != null) add("a.action_type = :actionType")
            if (query.userId != null) add("a.user_id = :userId::uuid")
            if (query.entityType != null) add("a.entity_type = :entityType")
            if (query.from != null) add("a.created_at >= :from::timestamptz")
            if (query.to != null) add("a.created_at <= :to::timestamptz")
        }.joinToString(prefix = "WHERE ", separator = " AND ")

    private fun Row.toEntry() =
        ActivityLogEntry(
            id = asUuid("id"),
            orgId = asUuid("org_id").toOrgId(),
            userId = asUuidOrNull("user_id")?.toUserId(),
            username = asString("username"),
            actionType = AuditActionType.entries.first { it.code == asString("action_type") },
            entityType = asStringOrNull("entity_type"),
            entityId = asUuidOrNull("entity_id"),
            subjectTitle = asStringOrNull("subject_title"),
            subjectTargetId = asUuidOrNull("subject_target_id"),
            data = asStringOrNull("data"),
            ipAddress = asStringOrNull("ip_address"),
            createdAt = asString("created_at"),
        )

    private companion object {
        val SELECT_SQL =
            """
            SELECT a.id, a.org_id, a.user_id, a.username, a.action_type, a.entity_type, a.entity_id,
                   a.data::text AS data, a.ip_address, a.created_at::text AS created_at,
                   CASE WHEN a.entity_type = 'client_doc' THEN cd.client_id ELSE NULL END AS subject_target_id,
                   CASE a.entity_type
                     WHEN 'client' THEN c.name
                     WHEN 'client_doc' THEN cd.name
                     WHEN 'group' THEN g.name
                     WHEN 'employee' THEN e.name
                     WHEN 'session' THEN CASE WHEN s.id IS NULL THEN NULL
                           ELSE COALESCE(sg.name || ' · ', '') || s.date::text END
                     WHEN 'branch' THEN b.name
                     WHEN 'hall' THEN h.name
                     WHEN 'discipline' THEN d.name
                     WHEN 'lead_source' THEN ls.name
                     WHEN 'subscription_tariff' THEN st.name
                     WHEN 'channel_integration' THEN ci.name
                     WHEN 'enrollment' THEN CASE WHEN en.id IS NULL THEN NULL
                           ELSE ec.name || ' · ' || eg.name END
                     WHEN 'membership' THEN m.name
                     WHEN 'upload' THEN u.original_name
                     ELSE NULL
                   END AS subject_title
            FROM audit_logs a
            LEFT JOIN clients c ON a.entity_type = 'client' AND c.id = a.entity_id AND c.org_id = a.org_id
            LEFT JOIN client_docs cd ON a.entity_type = 'client_doc' AND cd.id = a.entity_id AND cd.org_id = a.org_id
            LEFT JOIN groups g ON a.entity_type = 'group' AND g.id = a.entity_id AND g.org_id = a.org_id
            LEFT JOIN employees e ON a.entity_type = 'employee' AND e.id = a.entity_id AND e.org_id = a.org_id
            LEFT JOIN sessions s ON a.entity_type = 'session' AND s.id = a.entity_id AND s.org_id = a.org_id
            LEFT JOIN groups sg ON s.group_id = sg.id AND sg.org_id = s.org_id
            LEFT JOIN branches b ON a.entity_type = 'branch' AND b.id = a.entity_id AND b.org_id = a.org_id
            LEFT JOIN halls h ON a.entity_type = 'hall' AND h.id = a.entity_id AND h.org_id = a.org_id
            LEFT JOIN disciplines d ON a.entity_type = 'discipline' AND d.id = a.entity_id AND d.org_id = a.org_id
            LEFT JOIN lead_sources ls ON a.entity_type = 'lead_source' AND ls.id = a.entity_id AND ls.org_id = a.org_id
            LEFT JOIN subscription_tariffs st ON a.entity_type = 'subscription_tariff' AND st.id = a.entity_id AND st.org_id = a.org_id
            LEFT JOIN channel_integrations ci ON a.entity_type = 'channel_integration' AND ci.id = a.entity_id AND ci.org_id = a.org_id
            LEFT JOIN enrollments en ON a.entity_type = 'enrollment' AND en.id = a.entity_id
            LEFT JOIN clients ec ON ec.id = en.client_id AND ec.org_id = a.org_id
            LEFT JOIN groups eg ON eg.id = en.group_id AND eg.org_id = a.org_id
            LEFT JOIN memberships m ON a.entity_type = 'membership' AND m.id = a.entity_id AND m.org_id = a.org_id
            LEFT JOIN uploads u ON a.entity_type = 'upload' AND u.id = a.entity_id AND u.org_id = a.org_id
            """.trimIndent()
    }
}
