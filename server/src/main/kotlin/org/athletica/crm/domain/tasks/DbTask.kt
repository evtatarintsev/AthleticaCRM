package org.athletica.crm.domain.tasks

import arrow.core.raise.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.datetime.toJavaInstant
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskAttachmentId
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.core.tasks.toTaskAttachmentId
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asUuid
import java.time.Instant
import kotlin.time.Clock

internal data class DbTask(
    override val id: TaskId,
    override val orgId: OrgId,
    override val createdBy: EmployeeId,
    override val assigneeId: EmployeeId?,
    override val clientId: ClientId?,
    override val title: String,
    override val description: String,
    override val status: TaskStatus,
    override val dueDate: kotlin.time.Instant?,
    override val dueDateEnd: kotlin.time.Instant?,
    override val completedAt: kotlin.time.Instant?,
    override val createdAt: kotlin.time.Instant,
    override val updatedAt: kotlin.time.Instant,
) : Task {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        tr.sql(
            """
            UPDATE tasks
            SET title = :title, description = :description, assignee_id = :assigneeId, client_id = :clientId,
                due_date = :dueDate, due_date_end = :dueDateEnd, updated_at = :updatedAt
            WHERE id = :id AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("title", title)
            .bind("description", description)
            .bind("assigneeId", assigneeId)
            .bind("clientId", clientId)
            .bind("dueDate", dueDate)
            .bind("dueDateEnd", dueDateEnd)
            .bind("updatedAt", Clock.System.now().toJavaInstant())
            .bind("id", id)
            .bind("orgId", orgId)
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun updateStatus(status: TaskStatus) {
        val now = Clock.System.now()
        val completedAt = if (status == TaskStatus.COMPLETED) now.toJavaInstant() else null
        val newStatus = if (status == TaskStatus.COMPLETED) TaskStatus.COMPLETED else status

        tr.sql(
            """
            UPDATE tasks
            SET status = :status, completed_at = :completedAt, updated_at = :updatedAt
            WHERE id = :id AND org_id = :orgId
            """.trimIndent(),
        )
            .bind("status", newStatus.name)
            .bind("completedAt", completedAt)
            .bind("updatedAt", now.toJavaInstant())
            .bind("id", id)
            .bind("orgId", orgId)
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun attachUpload(uploadId: UploadId) {
        try {
            tr.sql(
                """
                INSERT INTO task_attachments (id, task_id, upload_id, uploaded_at, uploaded_by)
                VALUES (:id, :taskId, :uploadId, :uploadedAt, :uploadedBy)
                """.trimIndent(),
            )
                .bind("id", TaskAttachmentId.new())
                .bind("taskId", id)
                .bind("uploadId", uploadId)
                .bind("uploadedAt", Clock.System.now().toJavaInstant())
                .bind("uploadedBy", ctx.employeeId.value)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("TASK_ATTACHMENT_FAILED", "Не удалось прикрепить файл"))
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun detachUpload(uploadId: UploadId) {
        tr.sql(
            """
            DELETE FROM task_attachments
            WHERE task_id = :taskId AND upload_id = :uploadId
            """.trimIndent(),
        )
            .bind("taskId", id)
            .bind("uploadId", uploadId)
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun attachmentUploadIds(): List<UploadId> {
        return tr
            .sql(
                """
                SELECT upload_id
                FROM task_attachments
                WHERE task_id = :taskId
                ORDER BY uploaded_at DESC
                """.trimIndent(),
            )
            .bind("taskId", id)
            .list { row -> row.asUuid("upload_id").toUploadId() }
    }
}


