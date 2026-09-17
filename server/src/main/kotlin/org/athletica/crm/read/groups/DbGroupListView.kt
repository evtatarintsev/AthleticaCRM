package org.athletica.crm.read.groups

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.groups.GroupEmployee
import org.athletica.crm.api.schemas.groups.GroupListItem
import org.athletica.crm.api.schemas.groups.GroupListResponse
import org.athletica.crm.api.schemas.groups.GroupSelectItem
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLong
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * Реализация [GroupListView] поверх PostgreSQL. Расписание (с названиями залов)
 * и тренеры собираются JSONB-агрегатами в том же запросе, что и сами группы.
 */
class DbGroupListView : GroupListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(query: GroupListQuery): GroupListResponse {
        val groups =
            tr
                .sql(
                    """
                    SELECT g.id, g.name, $SCHEDULE_JSON AS schedule, $EMPLOYEES_JSON AS employees
                    FROM groups g
                    WHERE g.org_id = :orgId AND g.branch_id = :branchId${query.filters()}
                    ORDER BY g.name
                    """.trimIndent(),
                )
                .bindScope()
                .bindFilters(query)
                .list { row -> row.toListItem() }

        return GroupListResponse(groups = groups, total = totalCount())
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun forSelect(): List<GroupSelectItem> =
        tr
            .sql(
                """
                SELECT g.id, g.name
                FROM groups g
                WHERE g.org_id = :orgId AND g.branch_id = :branchId
                ORDER BY g.name
                """.trimIndent(),
            )
            .bindScope()
            .list { row -> GroupSelectItem(row.asUuid("id").toGroupId(), row.asString("name")) }

    /** Считает все группы организации и филиала — без фильтров [GroupListQuery]. */
    context(ctx: EmployeeRequestContext, tr: Transaction)
    private suspend fun totalCount(): UInt =
        tr
            .sql("SELECT COUNT(*) AS cnt FROM groups g WHERE g.org_id = :orgId AND g.branch_id = :branchId")
            .bindScope()
            .firstOrNull { row -> row.asLong("cnt") }
            ?.toUInt() ?: 0u

    /** Привязывает параметры организации и филиала. */
    context(ctx: EmployeeRequestContext)
    private fun QueryBuilder.bindScope(): QueryBuilder =
        bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)

    /** Подстрока поиска по названию; `null`, если фильтровать не нужно. */
    private val GroupListQuery.namePattern: String?
        get() = nameQuery?.trim()?.takeIf { it.isNotEmpty() }

    /** Условия фильтрации по названию, дисциплинам и тренерам. */
    private fun GroupListQuery.filters(): String =
        buildString {
            if (namePattern != null) {
                append(" AND g.name ILIKE :namePattern")
            }
            if (disciplineIds.isNotEmpty()) {
                append(
                    " AND EXISTS (SELECT 1 FROM group_disciplines gd" +
                        " WHERE gd.group_id = g.id AND gd.discipline_id = ANY(:disciplineIds))",
                )
            }
            if (employeeIds.isNotEmpty()) {
                append(
                    " AND EXISTS (SELECT 1 FROM group_employees ge" +
                        " WHERE ge.group_id = g.id AND ge.employee_id = ANY(:employeeIds))",
                )
            }
        }

    /** Привязывает параметры фильтров, объявленных в [GroupListQuery.filters]. */
    private fun QueryBuilder.bindFilters(query: GroupListQuery): QueryBuilder =
        let { q -> query.namePattern?.let { q.bind("namePattern", "%$it%") } ?: q }
            .let { q -> if (query.disciplineIds.isNotEmpty()) q.bind("disciplineIds", query.disciplineIds.map { it.value }) else q }
            .let { q -> if (query.employeeIds.isNotEmpty()) q.bind("employeeIds", query.employeeIds.map { it.value }) else q }

    private fun Row.toListItem(): GroupListItem =
        GroupListItem(
            id = asUuid("id").toGroupId(),
            name = asString("name"),
            schedule = Json.decodeFromString<List<ScheduleSlot>>(asString("schedule")),
            employees = Json.decodeFromString<List<GroupEmployee>>(asString("employees")),
        )

    internal companion object {
        /** Расписание группы с названиями залов, отсортированное по дню и времени начала. */
        val SCHEDULE_JSON =
            """
            (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                        'dayOfWeek', ss.day_of_week,
                        'startAt', ss.start_time,
                        'endAt', ss.end_time,
                        'hallId', ss.hall_id,
                        'hallName', h.name
                    ) ORDER BY ss.day_of_week, ss.start_time), '[]'::jsonb)
               FROM schedule_slots ss
               LEFT JOIN halls h ON h.id = ss.hall_id
              WHERE ss.group_id = g.id)
            """.trimIndent()

        /** Тренеры группы, отсортированные по имени. */
        val EMPLOYEES_JSON =
            """
            (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                        'id', e.id, 'name', e.name, 'avatarId', e.avatar_id
                    ) ORDER BY e.name), '[]'::jsonb)
               FROM group_employees ge
               JOIN employees e ON e.id = ge.employee_id
              WHERE ge.group_id = g.id)
            """.trimIndent()
    }
}
