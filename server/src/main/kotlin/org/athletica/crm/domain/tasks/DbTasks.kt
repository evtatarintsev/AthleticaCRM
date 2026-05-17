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
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.tasks.TaskId
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.core.tasks.toTaskId
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull
import java.time.Instant
import kotlin.time.Clock
import kotlin.time.toKotlinInstant

class DbTasks : Tasks {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: TaskId): Task {
        val task =
            tr
                .sql(
                    """
                    SELECT id, org_id, created_by, assignee_id, client_id, title, description, status,
                           due_date, due_date_end, completed_at, created_at, updated_at
                    FROM tasks
                    WHERE id = :id AND org_id = :orgId
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row ->
                    DbTask(
                        id = row.asUuid("id").toTaskId(),
                        orgId = row.asUuid("org_id").toOrgId(),
                        createdBy = row.asUuid("created_by").toEmployeeId(),
                        assigneeId = row.asUuidOrNull("assignee_id")?.toEmployeeId(),
                        clientId = row.asUuidOrNull("client_id")?.toClientId(),
                        title = row.asString("title"),
                        description = row.asString("description"),
                        status = TaskStatus.valueOf(row.asString("status")),
                        dueDate = row.asInstantOrNull("due_date"),
                        dueDateEnd = row.asInstantOrNull("due_date_end"),
                        completedAt = row.asInstantOrNull("completed_at"),
                        createdAt = row.asInstant("created_at"),
                        updatedAt = row.asInstant("updated_at"),
                    )
                }
                ?: raise(CommonDomainError("TASK_NOT_FOUND", "Задача не найдена"))

        return task
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(filter: TaskFilter): List<Task> {
        val sqlBuilder =
            StringBuilder(
                """
                SELECT id, org_id, created_by, assignee_id, client_id, title, description, status,
                       due_date, due_date_end, completed_at, created_at, updated_at
                FROM tasks
                WHERE org_id = :orgId
                """.trimIndent(),
            )

        if (filter.assigneeId != null) {
            sqlBuilder.append(" AND assignee_id = :assigneeId")
        }
        if (filter.createdBy != null) {
            sqlBuilder.append(" AND created_by = :createdBy")
        }
        if (!filter.status.isNullOrEmpty()) {
            sqlBuilder.append(" AND status = ANY(:status)")
        }
        if (filter.dueDateFrom != null) {
            sqlBuilder.append(" AND due_date >= :dueDateFrom")
        }
        if (filter.dueDateTo != null) {
            sqlBuilder.append(" AND due_date <= :dueDateTo")
        }
        if (filter.clientId != null) {
            sqlBuilder.append(" AND client_id = :clientId")
        }
        if (!filter.searchText.isNullOrBlank()) {
            sqlBuilder.append(" AND (title ILIKE :searchText OR description ILIKE :searchText)")
        }

        sqlBuilder.append(" ORDER BY created_at DESC")

        val statement =
            tr.sql(sqlBuilder.toString())
                .bind("orgId", ctx.orgId)

        if (filter.assigneeId != null) {
            statement.bind("assigneeId", filter.assigneeId)
        }
        if (filter.createdBy != null) {
            statement.bind("createdBy", filter.createdBy)
        }
        if (!filter.status.isNullOrEmpty()) {
            statement.bind("status", filter.status.map { it.name }.toTypedArray())
        }
        if (filter.dueDateFrom != null) {
            statement.bind("dueDateFrom", filter.dueDateFrom)
        }
        if (filter.dueDateTo != null) {
            statement.bind("dueDateTo", filter.dueDateTo)
        }
        if (filter.clientId != null) {
            statement.bind("clientId", filter.clientId)
        }
        if (!filter.searchText.isNullOrBlank()) {
            statement.bind("searchText", "%${filter.searchText}%")
        }

        return statement.list { row ->
            DbTask(
                id = row.asUuid("id").toTaskId(),
                orgId = row.asUuid("org_id").toOrgId(),
                createdBy = row.asUuid("created_by").toEmployeeId(),
                assigneeId = row.asUuidOrNull("assignee_id")?.toEmployeeId(),
                clientId = row.asUuidOrNull("client_id")?.toClientId(),
                title = row.asString("title"),
                description = row.asString("description"),
                status = TaskStatus.valueOf(row.asString("status")),
                dueDate = row.asInstantOrNull("due_date"),
                dueDateEnd = row.asInstantOrNull("due_date_end"),
                completedAt = row.asInstantOrNull("completed_at"),
                createdAt = row.asInstant("created_at"),
                updatedAt = row.asInstant("updated_at"),
            )
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: TaskId,
        orgId: OrgId,
        createdBy: EmployeeId,
        title: String,
        description: String,
        assigneeId: EmployeeId?,
        clientId: ClientId?,
        dueDate: kotlin.time.Instant?,
        dueDateEnd: kotlin.time.Instant?,
    ): Task {
        val now = Clock.System.now()
        val inserted =
            try {
                tr
                    .sql(
                        """
                        INSERT INTO tasks (id, org_id, created_by, assignee_id, client_id, title, description,
                                           status, due_date, due_date_end, created_at, updated_at)
                        VALUES (:id, :orgId, :createdBy, :assigneeId, :clientId, :title, :description,
                                :status, :dueDate, :dueDateEnd, :createdAt, :updatedAt)
                        """.trimIndent(),
                    )
                    .bind("id", id)
                    .bind("orgId", orgId)
                    .bind("createdBy", createdBy)
                    .bind("assigneeId", assigneeId)
                    .bind("clientId", clientId)
                    .bind("title", title)
                    .bind("description", description)
                    .bind("status", TaskStatus.PENDING.name)
                    .bind("dueDate", dueDate?.toJavaInstant())
                    .bind("dueDateEnd", dueDateEnd?.toJavaInstant())
                    .bind("createdAt", now.toJavaInstant())
                    .bind("updatedAt", now.toJavaInstant())
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("TASK_CREATE_FAILED", "Не удалось создать задачу"))
            }

        if (inserted == 0L) {
            raise(CommonDomainError("TASK_CREATE_FAILED", "Не удалось создать задачу"))
        }

        return DbTask(
            id = id,
            orgId = orgId,
            createdBy = createdBy,
            assigneeId = assigneeId,
            clientId = clientId,
            title = title,
            description = description,
            status = TaskStatus.PENDING,
            dueDate = dueDate,
            dueDateEnd = dueDateEnd,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
        )
    }
}

private fun java.util.UUID.toOrgId(): OrgId = OrgId(this)
private fun java.util.UUID.toEmployeeId(): EmployeeId = EmployeeId(this)
private fun java.util.UUID.toClientId(): ClientId = ClientId(this)

private fun java.util.UUID.toOrgId(): OrgId = OrgId(this)
private fun java.util.UUID.toEmployeeId(): EmployeeId = EmployeeId(this)
private fun java.util.UUID.toClientId(): ClientId = ClientId(this)
