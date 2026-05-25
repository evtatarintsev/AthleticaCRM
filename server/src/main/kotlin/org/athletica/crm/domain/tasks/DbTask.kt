package org.athletica.crm.domain.tasks

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

internal data class DbTask(
    override val id: TaskId,
    override val orgId: OrgId,
    override val createdBy: EmployeeId,
    override val assigneeId: EmployeeId?,
    override val clientId: ClientId?,
    override val title: String,
    override val description: String,
    override val status: TaskStatus,
    override val dueDate: Instant?,
    override val dueDateEnd: Instant?,
    override val completedAt: Instant?,
    override val createdAt: Instant,
    override val attachments: List<UploadId>,
    private val previousStatus: TaskStatus,
) : Task {
    context(tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        val now = Clock.System.now()
        val newCompletedAt =
            when {
                status == TaskStatus.COMPLETED && previousStatus != TaskStatus.COMPLETED -> now
                status != TaskStatus.COMPLETED -> null
                else -> completedAt
            }

        tr.sql(
            """
            UPDATE tasks
            SET assignee_id = :assigneeId, client_id = :clientId, title = :title,
                description = :description, status = :status, due_date = :dueDate,
                due_date_end = :dueDateEnd, completed_at = :completedAt, updated_at = :updatedAt
            WHERE id = :id AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("assigneeId", assigneeId)
            .bind("clientId", clientId)
            .bind("title", title)
            .bind("description", description)
            .bind("status", status.name)
            .bind("dueDate", dueDate)
            .bind("dueDateEnd", dueDateEnd)
            .bind("completedAt", newCompletedAt)
            .bind("updatedAt", now)
            .bind("id", id)
            .bind("orgId", orgId)
            .execute()

        if (attachments.isEmpty()) {
            tr.sql("DELETE FROM task_attachments WHERE task_id = :taskId")
                .bind("taskId", id)
                .execute()
        } else {
            tr.sql(
                """
                DELETE FROM task_attachments
                WHERE task_id = :taskId AND NOT (upload_id = ANY(:uploadIds))
                """.trimIndent(),
            )
                .bind("taskId", id)
                .bind("uploadIds", attachments)
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
        }
    }

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun withNew(
        newTitle: String,
        newDescription: String,
        newClientId: ClientId?,
        newDueDate: Instant?,
        newDueDateEnd: Instant?,
    ): Task {
        requireEditable()
        return copy(
            title = newTitle,
            description = newDescription,
            clientId = newClientId,
            dueDate = newDueDate,
            dueDateEnd = newDueDateEnd,
        )
    }

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun assignTo(employee: Employee): Task {
        requireEditable()
        return copy(assigneeId = employee.id)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun unassign(): Task {
        requireEditable()
        return copy(assigneeId = null)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun status(newStatus: TaskStatus): Task {
        requireEditable()
        return copy(status = newStatus)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun attach(uploadId: UploadId): Task {
        requireEditable()
        if (uploadId in attachments) {
            return this
        }
        return copy(attachments = attachments + uploadId)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    override fun detach(uploadId: UploadId): Task {
        requireEditable()
        return copy(attachments = attachments.filterNot { it == uploadId })
    }

    /** Проверяет право на редактирование задачи. Без [UserPermission.CAN_MANAGE_TASKS] разрешены только свои задачи. */
    context(ctx: EmployeeRequestContext, raise: Raise<DomainError>)
    private fun requireEditable() {
        if (ctx.hasPermission(UserPermission.CAN_MANAGE_TASKS)) return
        if (createdBy == ctx.employeeId || assigneeId == ctx.employeeId) return
        raise(CommonDomainError("PERMISSION_DENIED", "Нет прав для редактирования задачи $id"))
    }
}
