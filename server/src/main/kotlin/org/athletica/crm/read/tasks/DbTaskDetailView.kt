package org.athletica.crm.read.tasks

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.UploadId
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.core.tasks.toTaskId
import org.athletica.crm.read.uploads.UploadRow
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [TaskDetailView] поверх PostgreSQL. Имена создателя, исполнителя и клиента
 * приходят join'ами, вложения — JSONB-агрегатом, поэтому карточка собирается одним запросом.
 */
class DbTaskDetailView : TaskDetailView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: TaskId): TaskDetailRow =
        tr
            .sql(SQL)
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toDetailRow() }
            ?: raise(CommonDomainError("TASK_NOT_FOUND", "Задача не найдена"))

    /** Собирает карточку из строки выборки. */
    private fun Row.toDetailRow(): TaskDetailRow =
        TaskDetailRow(
            id = asUuid("id").toTaskId(),
            createdBy = asUuid("created_by").toEmployeeId(),
            createdByName = asStringOrNull("created_by_name") ?: "",
            assigneeId = asUuidOrNull("assignee_id")?.toEmployeeId(),
            assigneeName = asStringOrNull("assignee_name"),
            clientId = asUuidOrNull("client_id")?.toClientId(),
            clientName = asStringOrNull("client_name"),
            title = asString("title"),
            description = asString("description"),
            status = TaskStatus.valueOf(asString("status")),
            dueDate = asInstantOrNull("due_date"),
            dueDateEnd = asInstantOrNull("due_date_end"),
            completedAt = asInstantOrNull("completed_at"),
            createdAt = asInstant("created_at"),
            attachments =
                Json
                    .decodeFromString<List<AttachmentJson>>(asString("attachments"))
                    .map { it.toRow() },
        )

    /** Вложение в JSONB-агрегате запроса. */
    @Serializable
    private data class AttachmentJson(
        val id: UploadId,
        val objectKey: String,
        val originalName: String,
        val contentType: String,
        val sizeBytes: Long,
    ) {
        fun toRow(): UploadRow = UploadRow(id, objectKey, originalName, contentType, sizeBytes)
    }

    private companion object {
        val SQL =
            """
            SELECT
                t.id, t.created_by, cb.name AS created_by_name,
                t.assignee_id, a.name AS assignee_name,
                t.client_id, cl.name AS client_name,
                t.title, t.description, t.status, t.due_date, t.due_date_end,
                t.completed_at, t.created_at,
                (SELECT COALESCE(
                            jsonb_agg(
                                jsonb_build_object(
                                    'id', u.id,
                                    'objectKey', u.object_key,
                                    'originalName', u.original_name,
                                    'contentType', u.content_type,
                                    'sizeBytes', u.size_bytes
                                ) ORDER BY u.id
                            ),
                            '[]'::jsonb)
                   FROM task_attachments ta
                   JOIN uploads u ON u.id = ta.upload_id
                  WHERE ta.task_id = t.id) AS attachments
            FROM tasks t
            LEFT JOIN employees cb ON cb.id = t.created_by
            LEFT JOIN employees a ON a.id = t.assignee_id
            LEFT JOIN clients cl ON cl.id = t.client_id
            WHERE t.id = :id AND t.org_id = :orgId
            """.trimIndent()
    }
}
