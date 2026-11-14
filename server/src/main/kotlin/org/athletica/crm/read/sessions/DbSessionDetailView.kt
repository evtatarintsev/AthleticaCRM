package org.athletica.crm.read.sessions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.sessions.SessionDetailResponse
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

class DbSessionDetailView : SessionDetailView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun detail(id: SessionId): SessionDetailResponse =
        tr.sql(
            """
            SELECT s.group_id, g.name AS group_name, s.date, s.start_time, s.end_time, s.hall_id,
                   s.status::text AS status, s.origin_slot_id IS NULL AS is_manual, s.is_rescheduled, s.notes,
                   s.is_employee_assignment_overridden,
                   COALESCE((SELECT jsonb_agg(se.employee_id ORDER BY se.employee_id)
                             FROM session_employees se WHERE se.session_id = s.id), '[]'::jsonb) AS employee_ids
            FROM sessions s
            LEFT JOIN groups g ON g.id = s.group_id AND g.org_id = s.org_id
            WHERE s.id = :id AND s.org_id = :orgId
            """.trimIndent(),
        )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row ->
                val groupName =
                    row.asStringOrNull("group_name")
                        ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
                SessionDetailResponse(
                    id = id,
                    groupId = row.asUuid("group_id").toGroupId(),
                    groupName = groupName,
                    date = row.asLocalDate("date"),
                    startTime = row.asLocalTime("start_time"),
                    endTime = row.asLocalTime("end_time"),
                    hallId = row.asUuid("hall_id").toHallId(),
                    status = row.asString("status"),
                    isManual = row.asBoolean("is_manual"),
                    isRescheduled = row.asBoolean("is_rescheduled"),
                    notes = row.asStringOrNull("notes"),
                    employeeIds = Json.decodeFromString(row.asString("employee_ids")),
                    isEmployeeAssignmentOverridden = row.asBoolean("is_employee_assignment_overridden"),
                )
            } ?: raise(CommonDomainError("SESSION_NOT_FOUND", "Занятие не найдено"))
}
