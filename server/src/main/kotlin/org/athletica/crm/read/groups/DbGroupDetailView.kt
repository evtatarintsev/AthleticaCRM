package org.athletica.crm.read.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.groups.GroupClient
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupDiscipline
import org.athletica.crm.api.schemas.groups.GroupEmployee
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * Реализация [GroupDetailView] поверх PostgreSQL. Расписание, дисциплины, тренеры
 * и активные участники приходят JSONB-агрегатами в одном запросе.
 */
class DbGroupDetailView : GroupDetailView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: GroupId): GroupDetailResponse =
        tr
            .sql(SQL)
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toDetailResponse() }
            ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))

    private fun Row.toDetailResponse(): GroupDetailResponse =
        GroupDetailResponse(
            id = asUuid("id").toGroupId(),
            name = asString("name"),
            schedule = Json.decodeFromString<List<ScheduleSlot>>(asString("schedule")),
            scheduleChangeAt = asLocalDateOrNull("schedule_change_at"),
            disciplines = Json.decodeFromString<List<GroupDiscipline>>(asString("disciplines")),
            employees = Json.decodeFromString<List<GroupEmployee>>(asString("employees")),
            clients = Json.decodeFromString<List<GroupClient>>(asString("clients")),
        )

    private companion object {
        val SQL =
            """
            SELECT
                g.id,
                g.name,
                ${DbGroupListView.SCHEDULE_JSON} AS schedule,
                ${DbGroupListView.SCHEDULE_CHANGE_AT} AS schedule_change_at,
                ${DbGroupListView.EMPLOYEES_JSON} AS employees,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                            'id', d.id, 'name', d.name
                        ) ORDER BY d.name), '[]'::jsonb)
                   FROM group_disciplines gd
                   JOIN disciplines d ON d.id = gd.discipline_id
                  WHERE gd.group_id = g.id) AS disciplines,
                (SELECT COALESCE(jsonb_agg(jsonb_build_object(
                            'id', c.id, 'name', c.name
                        ) ORDER BY c.name), '[]'::jsonb)
                   FROM enrollments e
                   JOIN clients c ON c.id = e.client_id
                  WHERE e.group_id = g.id AND e.left_at IS NULL) AS clients
            FROM groups g
            WHERE g.id = :id AND g.org_id = :orgId
            """.trimIndent()
    }
}
