package org.athletica.crm.read.employees

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.employees.EmployeeAccessStatus
import org.athletica.crm.api.schemas.employees.EmployeeListItem
import org.athletica.crm.api.schemas.employees.EmployeeListResponse
import org.athletica.crm.api.schemas.employees.EmployeeRole
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toUploadId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asInstant
import org.athletica.crm.storage.asInstantOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import org.athletica.crm.storage.asUuidOrNull

/**
 * Реализация [EmployeeListView] поверх PostgreSQL. Роли каждого сотрудника приходят
 * JSONB-агрегатом, поэтому список собирается одним запросом.
 */
class DbEmployeeListView : EmployeeListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun list(query: EmployeeListQuery): EmployeeListResponse {
        val items =
            tr
                .sql(SQL)
                .bind("orgId", ctx.orgId)
                .bind("now", query.now)
                .list { row -> row.toListItem() }
        return EmployeeListResponse(items, items.size.toUInt())
    }

    /** Собирает элемент списка из строки выборки. */
    private fun Row.toListItem(): EmployeeListItem =
        EmployeeListItem(
            id = asUuid("id").toEmployeeId(),
            name = asString("name"),
            avatarId = asUuidOrNull("avatar_id")?.toUploadId(),
            isOwner = asBoolean("is_owner"),
            status = EmployeeAccessStatus.valueOf(asString("status")),
            lastSeenAt = asInstantOrNull("last_seen_at"),
            online = asBoolean("online"),
            joinedAt = asInstant("joined_at"),
            roles = Json.decodeFromString<List<EmployeeRole>>(asString("roles")),
        )

    companion object {
        /**
         * Колонки `status` и `online` выборки из `employees e` по параметру `:now`:
         * статус выводится из `blocked_at`, `user_id` и `last_seen_at`, «в сети» — активность за 5 минут.
         */
        internal val ACCESS_COLUMNS =
            """
            CASE
                WHEN e.blocked_at IS NOT NULL THEN 'BLOCKED'
                WHEN e.user_id IS NULL THEN 'NO_ACCESS'
                WHEN e.last_seen_at IS NULL THEN 'INVITED'
                ELSE 'ACTIVE'
            END AS status,
            (e.blocked_at IS NULL AND e.last_seen_at IS NOT NULL
                AND e.last_seen_at > CAST(:now AS TIMESTAMPTZ) - INTERVAL '5 minutes') AS online,
            """.trimIndent()

        val SQL =
            """
            SELECT
                e.id, e.name, e.avatar_id, e.is_owner, e.joined_at, e.last_seen_at,
                $ACCESS_COLUMNS
                (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', r.id, 'name', r.name) ORDER BY r.name), '[]'::jsonb)
                   FROM employee_roles er JOIN roles r ON r.id = er.role_id AND r.org_id = e.org_id
                  WHERE er.employee_id = e.id) AS roles
            FROM employees e
            WHERE e.org_id = :orgId
            ORDER BY e.is_owner DESC, e.name
            """.trimIndent()
    }
}
