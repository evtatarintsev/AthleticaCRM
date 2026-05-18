package org.athletica.crm.domain.tasks

import arrow.core.raise.context.Raise
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.ClientId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.OrgId
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.storage.Transaction
import kotlin.time.Clock
import kotlin.time.Instant

internal data class DbTask(
    override val id: TaskId,
    override val orgId: OrgId,
    override val createdBy: EmployeeId,
    override var assigneeId: EmployeeId?,
    override var clientId: ClientId?,
    override var title: String,
    override var description: String,
    override var status: TaskStatus,
    override var dueDate: Instant?,
    override var dueDateEnd: Instant?,
    override val completedAt: Instant?,
    override val createdAt: Instant,
    override var attachments: List<UploadId>,
    private val previousStatus: TaskStatus,
) : Task {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
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
}
