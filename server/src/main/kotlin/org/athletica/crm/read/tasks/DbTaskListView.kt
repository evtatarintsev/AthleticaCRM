package org.athletica.crm.read.tasks

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import org.athletica.crm.api.schemas.tasks.TaskListItemSchema
import org.athletica.crm.api.schemas.tasks.TaskListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toClientId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.permissions.UserPermission
import org.athletica.crm.core.tasks.TaskStatus
import org.athletica.crm.core.tasks.toTaskId
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [TaskListView] поверх PostgreSQL. Имена исполнителя и клиента
 * приходят из `LEFT JOIN`, поэтому дополнительных запросов за ними не требуется.
 */
class DbTaskListView : TaskListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun page(query: TaskListQuery): TaskListResponse {
        val total =
            buildQuery(query, "COUNT(*) AS cnt", paginate = false)
                .firstOrNull { row -> row.asLong("cnt") }
                ?.toUInt() ?: 0u
        if (total == 0u) {
            return TaskListResponse(emptyList(), 0u)
        }

        val tasks =
            buildQuery(query, SELECT_COLUMNS, paginate = true)
                .bind("limit", query.limit.toLong())
                .bind("offset", query.offset.toLong())
                .list { row -> row.toListItem() }

        return TaskListResponse(tasks, total)
    }

    /**
     * Строит запрос с динамическим `WHERE` по [query]. [select] — список выбираемых
     * колонок, [paginate] добавляет сортировку и пагинацию.
     */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    private fun buildQuery(query: TaskListQuery, select: String, paginate: Boolean): QueryBuilder {
        val conditions = mutableListOf("t.org_id = :orgId")

        if (!ctx.hasPermission(UserPermission.CAN_VIEW_ALL_TASKS) || query.onlyMine) {
            conditions += "(t.assignee_id = :me OR t.created_by = :me)"
        }
        if (query.statuses.isNotEmpty()) {
            conditions += "t.status = ANY(:statuses)"
        }
        if (query.dueDateFrom != null) {
            conditions += "t.due_date >= :dueDateFrom"
        }
        if (query.dueDateTo != null) {
            conditions += "t.due_date <= :dueDateTo"
        }
        if (query.clientId != null) {
            conditions += "t.client_id = :clientId"
        }
        if (query.searchText != null) {
            conditions += "(t.title ILIKE :search OR t.description ILIKE :search)"
        }

        val pagination = if (paginate) " ORDER BY t.created_at DESC LIMIT :limit OFFSET :offset" else ""
        val sql =
            """
            SELECT $select
            FROM tasks t
            LEFT JOIN employees a ON a.id = t.assignee_id
            LEFT JOIN clients cl ON cl.id = t.client_id
            WHERE ${conditions.joinToString(" AND ")}$pagination
            """.trimIndent()

        return tr
            .sql(sql)
            .bind("orgId", ctx.orgId)
            .bind("me", ctx.employeeId)
            .let { q ->
                if (query.statuses.isNotEmpty()) {
                    q.bind("statuses", query.statuses.map { it.name }.toTypedArray())
                } else {
                    q
                }
            }
            .let { q -> if (query.dueDateFrom != null) q.bind("dueDateFrom", query.dueDateFrom) else q }
            .let { q -> if (query.dueDateTo != null) q.bind("dueDateTo", query.dueDateTo) else q }
            .let { q -> if (query.clientId != null) q.bind("clientId", query.clientId) else q }
            .let { q -> if (query.searchText != null) q.bind("search", "%${query.searchText}%") else q }
    }

    /** Собирает элемент списка из строки выборки. */
    private fun Row.toListItem(): TaskListItemSchema =
        TaskListItemSchema(
            id = asUuid("id").toTaskId(),
            title = asString("title"),
            assigneeId = asUuidOrNull("assignee_id")?.toEmployeeId(),
            assigneeName = asStringOrNull("assignee_name"),
            clientId = asUuidOrNull("client_id")?.toClientId(),
            clientName = asStringOrNull("client_name"),
            status = TaskStatus.valueOf(asString("status")),
            dueDate = asInstantOrNull("due_date"),
            dueDateEnd = asInstantOrNull("due_date_end"),
        )

    private companion object {
        val SELECT_COLUMNS =
            """
            t.id, t.title, t.assignee_id, a.name AS assignee_name,
            t.client_id, cl.name AS client_name, t.status, t.due_date, t.due_date_end
            """.trimIndent()
    }
}
