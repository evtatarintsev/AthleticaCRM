package org.athletica.crm.routes

import org.athletica.crm.api.schemas.audit.AuditLogItem
import org.athletica.crm.api.schemas.audit.AuditLogListRequest
import org.athletica.crm.api.schemas.audit.AuditLogListResponse
import org.athletica.crm.api.schemas.audit.AuditLogSubject
import org.athletica.crm.api.schemas.audit.AuditLogUsersResponse
import org.athletica.crm.domain.audit.AuditActionType
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.ReadViews
import org.athletica.crm.read.audit.ActivityLogEntry
import org.athletica.crm.read.audit.ActivityLogQuery
import org.athletica.crm.storage.Database

/** Регистрирует маршрут GET /audit/log — локализованный список действий с пагинацией и фильтрами. */
context(db: Database)
fun RouteWithContext.auditRoutes(
    views: ReadViews,
) {
    route("/audit") {
        get<Unit, AuditLogUsersResponse>("/log/users") {
            db.transaction { AuditLogUsersResponse(views.activityLog.users()) }
        }
        get<AuditLogListRequest, AuditLogListResponse>("/log") { request ->
            val page = request.page.coerceAtLeast(0)
            val pageSize = request.pageSize.coerceIn(1, 200)
            val query =
                ActivityLogQuery(
                    limit = pageSize.toUInt(),
                    offset = (page * pageSize).toUInt(),
                    actionType = request.actionType?.let { code -> AuditActionType.entries.firstOrNull { it.code == code } },
                    userId = request.userId,
                    entityType = request.entityType,
                    from = request.from,
                    to = request.to,
                )

            db.transaction {
                val items = views.activityLog.list(query)
                val total = views.activityLog.count(query)
                AuditLogListResponse(
                    items = items.map { it.toAuditLogItem() },
                    total = total,
                    page = page,
                    pageSize = pageSize,
                )
            }
        }
    }
}

/** Преобразует строку read-проекции в API-модель и строит локализованное краткое описание. */
context(ctx: org.athletica.crm.core.EmployeeRequestContext)
private fun ActivityLogEntry.toAuditLogItem(): AuditLogItem {
    val subject =
        if (entityType != null && entityId != null) {
            AuditLogSubject(
                type = entityType,
                id = entityId,
                title = subjectTitle ?: Messages.AuditSubjectUnavailable.localize(ctx.lang),
                isAvailable = subjectTitle != null,
                targetId = subjectTargetId,
            )
        } else {
            null
        }
    val action = Messages.auditAction(actionType, ctx.lang)
    val subjectText =
        when {
            subject == null -> null
            subjectTitle == null -> "${Messages.auditEntity(entityType, ctx.lang)} ${Messages.AuditSubjectUnavailable.localize(ctx.lang)}"
            else -> "${Messages.auditEntity(entityType, ctx.lang)} «${subject.title}»"
        }
    val details = listOfNotNull(action, subjectText).joinToString(" ")

    return AuditLogItem(
        id = id,
        userId = userId?.value,
        username = username,
        actionType = actionType.code,
        description = Messages.AuditLogEntry.localize(ctx.lang, username, details),
        entityType = entityType,
        entityId = entityId,
        subject = subject,
        data = data,
        ipAddress = ipAddress,
        createdAt = createdAt,
    )
}
