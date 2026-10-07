package org.athletica.crm.read.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.groups.GroupSessionSchema
import org.athletica.crm.api.schemas.groups.GroupSessionsResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.read.SessionSql
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull

/**
 * Реализация [GroupSessionsView] поверх PostgreSQL одним запросом: строки периода,
 * последнее и ближайшее занятия собираются JSONB-подзапросами от строки группы.
 * Скоуп по организации и филиалу — через группу; «наступило ли занятие» считается
 * по часовому поясу организации.
 */
class DbGroupSessionsView : GroupSessionsView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(query: GroupSessionsQuery): GroupSessionsResponse =
        tr
            .sql(SQL)
            .bind("groupId", query.groupId)
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .bind("from", query.from)
            .bind("to", query.to)
            .firstOrNull { row -> row.toResponse() }
            ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))

    /** Собирает ответ из JSON-колонок строки выборки. */
    private fun Row.toResponse(): GroupSessionsResponse =
        GroupSessionsResponse(
            sessions = Json.decodeFromString<List<GroupSessionSchema>>(asString("sessions")),
            last = asStringOrNull("last")?.let { Json.decodeFromString<GroupSessionSchema>(it) },
            next = asStringOrNull("next")?.let { Json.decodeFromString<GroupSessionSchema>(it) },
        )

    private companion object {
        /** Строка занятия под псевдонимом `s` с залом `h` в форме [GroupSessionSchema]. */
        val SESSION_JSON =
            """
            jsonb_build_object(
                'id', s.id, 'date', s.date, 'startTime', s.start_time, 'endTime', s.end_time,
                'hall', jsonb_build_object('id', h.id, 'name', h.name),
                'coaches', ${SessionSql.COACHES_JSON},
                'coachesOverridden', s.is_employee_assignment_overridden,
                'status', upper(s.status::text),
                'rescheduledFrom', CASE WHEN s.is_rescheduled AND s.origin_date <> s.date THEN s.origin_date END,
                'isManual', s.origin_slot_id IS NULL,
                'attendance', CASE WHEN s.status = 'completed' THEN
                    (SELECT jsonb_build_object('present', COUNT(*) FILTER (WHERE a.presence = 'present'), 'total', COUNT(*))
                       FROM session_attendance a
                      WHERE a.session_id = s.id)
                END
            )
            """.trimIndent()

        /** Момент начала занятия `s` в часовом поясе организации `o`. */
        const val STARTS_AT = "(s.date + s.start_time) AT TIME ZONE o.timezone"

        val SQL =
            """
            SELECT
                (SELECT COALESCE(jsonb_agg($SESSION_JSON ORDER BY s.date, s.start_time, s.id), '[]'::jsonb)
                   FROM sessions s
                   JOIN halls h ON h.id = s.hall_id
                  WHERE s.group_id = g.id AND s.org_id = g.org_id AND s.date BETWEEN :from AND :to) AS sessions,
                (SELECT $SESSION_JSON
                   FROM sessions s
                   JOIN halls h ON h.id = s.hall_id
                  WHERE s.group_id = g.id AND s.org_id = g.org_id AND s.status <> 'cancelled'
                    AND $STARTS_AT <= now()
                  ORDER BY s.date DESC, s.start_time DESC, s.id DESC
                  LIMIT 1) AS last,
                (SELECT $SESSION_JSON
                   FROM sessions s
                   JOIN halls h ON h.id = s.hall_id
                  WHERE s.group_id = g.id AND s.org_id = g.org_id AND s.status = 'scheduled'
                    AND $STARTS_AT > now()
                  ORDER BY s.date, s.start_time, s.id
                  LIMIT 1) AS next
            FROM groups g
            JOIN organizations o ON o.id = g.org_id
            WHERE g.id = :groupId AND g.org_id = :orgId AND g.branch_id = :branchId
            """.trimIndent()
    }
}
