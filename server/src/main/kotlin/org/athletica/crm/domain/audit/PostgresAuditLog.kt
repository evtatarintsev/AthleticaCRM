package org.athletica.crm.domain.audit

import arrow.core.raise.Raise
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction

class PostgresAuditLog : AuditLog {
    context(tr: Transaction, raise: Raise<AppError>)
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
}
