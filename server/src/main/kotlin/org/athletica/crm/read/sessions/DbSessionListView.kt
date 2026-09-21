package org.athletica.crm.read.sessions

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.sessions.SessionListItem
import org.athletica.crm.api.schemas.sessions.SessionListResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/**
 * Реализация [SessionListView] поверх PostgreSQL: название группы приходит join'ом,
 * состав тренеров — JSONB-агрегатом в том же запросе.
 */
class DbSessionListView : SessionListView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(query: SessionListQuery): SessionListResponse {
        val groupFilter = if (query.groupId != null) " AND s.group_id = :groupId" else ""
        val sessions =
            tr
                .sql(
                    """
                    SELECT s.id, s.group_id, g.name AS group_name, s.date, s.start_time, s.end_time, s.hall_id,
                           s.status, s.is_rescheduled, s.notes, s.is_employee_assignment_overridden,
                           (s.origin_slot_id IS NULL) AS is_manual,
                           $EMPLOYEE_IDS_JSON AS employee_ids
                    FROM sessions s
                    JOIN groups g ON g.id = s.group_id
                    WHERE s.org_id = :orgId AND s.date >= :from AND s.date <= :to$groupFilter
                    ORDER BY s.date, s.start_time, s.id
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("from", query.from)
                .bind("to", query.to)
                .let { q -> if (query.groupId != null) q.bind("groupId", query.groupId) else q }
                .list { row -> row.toListItem() }
        return SessionListResponse(sessions)
    }

    /** Собирает элемент списка из строки выборки. */
    private fun Row.toListItem(): SessionListItem =
        SessionListItem(
            id = asUuid("id").toSessionId(),
            groupId = asUuid("group_id").toGroupId(),
            groupName = asString("group_name"),
            date = asLocalDate("date"),
            startTime = asLocalTime("start_time"),
            endTime = asLocalTime("end_time"),
            hallId = asUuid("hall_id").toHallId(),
            status = asString("status"),
            isManual = asBoolean("is_manual"),
            isRescheduled = asBoolean("is_rescheduled"),
            notes = asStringOrNull("notes"),
            employeeIds = Json.decodeFromString<List<EmployeeId>>(asString("employee_ids")),
            isEmployeeAssignmentOverridden = asBoolean("is_employee_assignment_overridden"),
        )

    private companion object {
        /** Идентификаторы тренеров занятия. */
        val EMPLOYEE_IDS_JSON =
            """
            (SELECT COALESCE(jsonb_agg(se.employee_id ORDER BY se.employee_id), '[]'::jsonb)
               FROM session_employees se
              WHERE se.session_id = s.id)
            """.trimIndent()
    }
}
