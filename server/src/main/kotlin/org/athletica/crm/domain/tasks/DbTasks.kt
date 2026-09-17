package org.athletica.crm.domain.tasks

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.core.tasks.toTaskId
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import kotlin.time.Clock
import kotlin.time.Instant

/** Реализация репозитория задач на основе PostgreSQL через R2DBC. */
class DbTasks : Tasks {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: TaskId): Task = byIds(listOf(id)).first()

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<TaskId>): List<Task> {
        if (ids.isEmpty()) return emptyList()

        val rows =
            tr.sql(
                """
                SELECT id, org_id, created_by, assignee_id, client_id, title, description,
                       status, due_date, due_date_end, completed_at, created_at
                FROM tasks
                WHERE id = ANY(:ids) AND org_id = :orgId
                """.trimIndent(),
            )
                .bind("ids", ids)
                .bind("orgId", ctx.orgId)
                .list { row -> row.toDbTask(emptyList()) }

        val foundIds = rows.map { it.id }.toSet()
        val missing = ids.filter { it !in foundIds }
        if (missing.isNotEmpty()) {
            raise(CommonDomainError("TASK_NOT_FOUND", "Задача не найдена"))
        }

        val attachmentsByTask = loadAttachments(rows.map { it.id }, tr)
        val byId = rows.associate { it.id to it.copy(attachments = attachmentsByTask[it.id] ?: emptyList()) }
        return ids.map { byId.getValue(it) }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: TaskId,
        title: String,
        description: String,
        clientId: ClientId?,
        dueDate: Instant?,
        dueDateEnd: Instant?,
    ): Task {
        val now = Clock.System.now()
        tr.sql(
            """
            INSERT INTO tasks (id, org_id, created_by, assignee_id, client_id, title, description,
                               status, due_date, due_date_end, created_at, updated_at)
            VALUES (:id, :orgId, :createdBy, NULL, :clientId, :title, :description,
                   'PENDING', :dueDate, :dueDateEnd, :now, :now)
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("createdBy", ctx.employeeId)
            .bind("clientId", clientId)
            .bind("title", title)
            .bind("description", description)
            .bind("dueDate", dueDate)
            .bind("dueDateEnd", dueDateEnd)
            .bind("now", now)
            .execute()

        return DbTask(
            id = id,
            orgId = ctx.orgId,
            createdBy = ctx.employeeId,
            assigneeId = null,
            clientId = clientId,
            title = title,
            description = description,
            status = TaskStatus.PENDING,
            dueDate = dueDate,
            dueDateEnd = dueDateEnd,
            completedAt = null,
            createdAt = now,
            attachments = emptyList(),
            previousStatus = TaskStatus.PENDING,
        )
    }

    /** Загружает вложения для списка задач одним запросом. */
    private suspend fun loadAttachments(
        taskIds: List<TaskId>,
        tr: Transaction,
    ): Map<TaskId, List<org.athletica.crm.core.entityids.UploadId>> {
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

    private fun io.r2dbc.spi.Row.toDbTask(attachments: List<org.athletica.crm.core.entityids.UploadId>): DbTask =
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
