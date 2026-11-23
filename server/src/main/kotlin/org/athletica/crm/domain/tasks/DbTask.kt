package org.athletica.crm.domain.tasks

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

internal data class DbTask(
    override val id: TaskId,
    override val orgId: OrgId,
    override val createdBy: EmployeeId?,
    override val assigneeId: EmployeeId?,
    override val clientId: ClientId?,
    override val title: String,
    override val description: String,
    override val state: TaskState,
    override val dueDate: Instant?,
    override val dueDateEnd: Instant?,
    override val createdAt: Instant,
    override val attachments: List<UploadId>,
    /** Вложения на момент загрузки из БД; [save] пишет только разницу с ними. */
    private val persistedAttachments: List<UploadId>,
    /** Сохранение этой копии захватывает неназначенную задачу. */
    private val claimingUnassigned: Boolean = false,
    /** Источник времени для момента завершения и `updated_at`. */
    private val clock: Clock,
) : Task {
    context(tr: Transaction, raise: Raise<AppError>)
    override suspend fun save() {
        val update =
            tr.sql(
                """
                UPDATE tasks
                SET assignee_id = :assigneeId, client_id = :clientId, title = :title,
                    description = :description, status = :status, due_date = :dueDate,
                    due_date_end = :dueDateEnd, completed_at = :completedAt, updated_at = :updatedAt
                WHERE id = :id AND org_id = :orgId
                  AND (NOT :claimingUnassigned OR assignee_id IS NULL)
                """.trimIndent(),
            )
                .bind("assigneeId", assigneeId)
                .bind("clientId", clientId)
                .bind("title", title)
                .bind("description", description)
                .bind("status", state.status.name)
                .bind("dueDate", dueDate)
                .bind("dueDateEnd", dueDateEnd)
                .bind("completedAt", (state as? TaskState.Completed)?.at)
                .bind("updatedAt", clock.now())
                .bind("id", id)
                .bind("orgId", orgId)
                .bind("claimingUnassigned", claimingUnassigned)
                .execute()

        if (claimingUnassigned && update != 1L) {
            raise(CommonDomainError("TASK_ALREADY_ASSIGNED", "Задача $id уже назначена другому сотруднику"))
        }

        val removed = persistedAttachments - attachments.toSet()
        if (removed.isNotEmpty()) {
            tr.sql("DELETE FROM task_attachments WHERE task_id = :taskId AND upload_id = ANY(:uploadIds)")
                .bind("taskId", id)
                .bind("uploadIds", removed)
                .execute()
        }

        (attachments - persistedAttachments.toSet()).forEach { uploadId ->
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

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun withNew(
        newTitle: NonBlankName,
        newDescription: String,
        newClientId: ClientId?,
        newDueDate: Instant?,
        newDueDateEnd: Instant?,
    ): Task {
        requireEditable()
        return copy(
            title = newTitle.value,
            description = newDescription,
            clientId = newClientId,
            dueDate = newDueDate,
            dueDateEnd = newDueDateEnd,
        )
    }

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun assignTo(employee: Employee): Task {
        val takesForSelf = assigneeId == null && employee.id == ctx.employee.id
        if (!takesForSelf) {
            requireEditable()
        }
        return copy(assigneeId = employee.id, claimingUnassigned = takesForSelf)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun unassign(): Task {
        requireEditable()
        return copy(assigneeId = null)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun complete(): Task {
        requireEditable()
        if (state is TaskState.Completed) {
            return this
        }
        return copy(state = TaskState.Completed(clock.now()))
    }

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun moveTo(to: TaskState.Open): Task {
        requireEditable()
        return copy(state = to)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun attach(uploadId: UploadId): Task {
        requireEditable()
        if (uploadId in attachments) {
            return this
        }
        return copy(attachments = attachments + uploadId)
    }

    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    override fun detach(uploadId: UploadId): Task {
        requireEditable()
        return copy(attachments = attachments.filterNot { it == uploadId })
    }

    /**
     * Проверяет право на редактирование задачи. Без [UserPermission.CAN_MANAGE_TASKS] разрешены
     * только свои задачи (автор или исполнитель); неназначенную чужую задачу сначала нужно взять себе.
     */
    context(ctx: EmployeeRequestContext, raise: Raise<AppError>)
    private fun requireEditable() {
        if (ctx.hasPermission(UserPermission.CAN_MANAGE_TASKS) || createdBy == ctx.employee.id || assigneeId == ctx.employee.id) {
            return
        }
        raise(CommonDomainError("PERMISSION_DENIED", "Нет прав для редактирования задачи $id"))
    }
}
