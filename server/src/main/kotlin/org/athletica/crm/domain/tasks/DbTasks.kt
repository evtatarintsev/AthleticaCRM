package org.athletica.crm.domain.tasks

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.Permission
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.core.tasks.toTaskId
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Реализация репозитория задач на основе PostgreSQL через R2DBC. */
class DbTasks : Tasks {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: TaskId): Task {
        val task =
            tr.sql(
                """
                SELECT id, org_id, created_by, assignee_id, client_id, title, description,
                       status, due_date, due_date_end, completed_at, created_at
                FROM tasks
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row -> row.toDbTask(emptyList()) }
                ?: raise(CommonDomainError("TASK_NOT_FOUND", "Задача не найдена"))

        val attachments = loadAttachments(listOf(task.id), tr)[task.id] ?: emptyList()
        return task.copy(attachments = attachments)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(filter: TaskFilter): TaskList {
        val total =
            buildQuery(filter, "COUNT(*) AS cnt", paginate = false)
                .firstOrNull { row -> row.asLong("cnt") }?.toUInt() ?: 0u

        val tasks =
            buildQuery(
                filter,
                """
                t.id, t.org_id, t.created_by, t.assignee_id, t.client_id, t.title, t.description,
                t.status, t.due_date, t.due_date_end, t.completed_at, t.created_at
                """.trimIndent(),
                paginate = true,
            )
                .bind("limit", filter.limit.toLong())
                .bind("offset", filter.offset.toLong())
                .list { row -> row.toDbTask(emptyList()) }

        val attachmentsByTask = loadAttachments(tasks.map { it.id }, tr)
        val items = tasks.map { it.copy(attachments = attachmentsByTask[it.id] ?: emptyList()) }
        return TaskList(items, total)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: TaskId,
        title: String,
        description: String,
        assigneeId: EmployeeId?,
        clientId: ClientId?,
        dueDate: Instant?,
        dueDateEnd: Instant?,
        attachments: List<UploadId>,
    ): Task {
        val now = Clock.System.now()
        tr.sql(
            """
            INSERT INTO tasks (id, org_id, created_by, assignee_id, client_id, title, description,
                               status, due_date, due_date_end, created_at, updated_at)
            VALUES (:id, :orgId, :createdBy, :assigneeId, :clientId, :title, :description,
                   'PENDING', :dueDate, :dueDateEnd, :now, :now)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("createdBy", ctx.employeeId)
            .bind("assigneeId", assigneeId)
            .bind("clientId", clientId)
            .bind("title", title)
            .bind("description", description)
            .bind("dueDate", dueDate)
            .bind("dueDateEnd", dueDateEnd)
            .bind("now", now)
            .execute()

        attachments.forEach { uploadId ->
            tr.sql(
                """
                INSERT INTO task_attachments (task_id, upload_id)
                VALUES (:taskId, :uploadId)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
            )
                .bind("taskId", id)
                .bind("uploadId", uploadId)
                .execute()
        }

        return DbTask(
            id = id,
            orgId = ctx.orgId,
            createdBy = ctx.employeeId,
            assigneeId = assigneeId,
            clientId = clientId,
            title = title,
            description = description,
            status = TaskStatus.PENDING,
            dueDate = dueDate,
            dueDateEnd = dueDateEnd,
            completedAt = null,
            createdAt = now,
            attachments = attachments,
            previousStatus = TaskStatus.PENDING,
        )
    }

    /** Строит запрос к таблице [tasks] с динамическим WHERE на основе [filter]. */
    context(ctx: RequestContext, tr: Transaction)
    private fun buildQuery(filter: TaskFilter, select: String, paginate: Boolean): QueryBuilder {
        val conditions = mutableListOf("t.org_id = :orgId")

        if (!ctx.hasPermission(Permission.CAN_VIEW_ALL_TASKS) || filter.onlyMine) {
            conditions += "(t.assignee_id = :me OR t.created_by = :me)"
        }
        if (filter.statuses.isNotEmpty()) {
            conditions += "t.status = ANY(:statuses)"
        }
        if (filter.dueDateFrom != null) {
            conditions += "t.due_date >= :dueDateFrom"
        }
        if (filter.dueDateTo != null) {
            conditions += "t.due_date <= :dueDateTo"
        }
        if (filter.clientId != null) {
            conditions += "t.client_id = :clientId"
        }
        if (filter.searchText != null) {
            conditions += "(t.title ILIKE :search OR t.description ILIKE :search)"
        }

        val where = conditions.joinToString(" AND ")
        val pagination = if (paginate) " ORDER BY t.created_at DESC LIMIT :limit OFFSET :offset" else ""
        val sql = "SELECT $select FROM tasks t WHERE $where$pagination"

        return tr.sql(sql)
            .bind("orgId", ctx.orgId)
            .bind("me", ctx.employeeId)
            .let { q ->
                if (filter.statuses.isNotEmpty()) {
                    q.bind("statuses", filter.statuses.map { it.name }.toTypedArray())
                } else {
                    q
                }
            }
            .let { q -> if (filter.dueDateFrom != null) q.bind("dueDateFrom", filter.dueDateFrom) else q }
            .let { q -> if (filter.dueDateTo != null) q.bind("dueDateTo", filter.dueDateTo) else q }
            .let { q -> if (filter.clientId != null) q.bind("clientId", filter.clientId) else q }
            .let { q ->
                if (filter.searchText != null) q.bind("search", "%${filter.searchText}%") else q
            }
    }

    /** Загружает вложения для списка задач одним запросом. */
    private suspend fun loadAttachments(
        taskIds: List<TaskId>,
        tr: Transaction,
    ): Map<TaskId, List<UploadId>> {
        if (taskIds.isEmpty()) return emptyMap()
        return tr.sql(
            "SELECT task_id, upload_id FROM task_attachments WHERE task_id = ANY(:taskIds)",
        )
            .bind("taskIds", taskIds)
            .list { row ->
                val taskId = row.asUuid("task_id").toTaskId()
                val uploadId = row.asUuid("upload_id").toUploadId()
                taskId to uploadId
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun io.r2dbc.spi.Row.toDbTask(attachments: List<UploadId>): DbTask =
        DbTask(
            id = asUuid("id").toTaskId(),
            orgId = org.athletica.crm.core.entityids.OrgId(asUuid("org_id")),
            createdBy = asUuid("created_by").toEmployeeId(),
            assigneeId = asUuidOrNull("assignee_id")?.toEmployeeId(),
            clientId = asUuidOrNull("client_id")?.toClientId(),
            title = asString("title"),
            description = asString("description"),
            status = TaskStatus.valueOf(asString("status")),
            dueDate = asInstantOrNull("due_date"),
            dueDateEnd = asInstantOrNull("due_date_end"),
            completedAt = asInstantOrNull("completed_at"),
            createdAt = asInstant("created_at"),
            attachments = attachments,
            previousStatus = TaskStatus.valueOf(asString("status")),
        )
}
