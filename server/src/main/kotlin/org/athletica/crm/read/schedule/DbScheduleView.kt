package org.athletica.crm.read.schedule

import arrow.core.raise.context.Raise
import io.r2dbc.spi.Row
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.schedule.ScheduleCoachSchema
import org.athletica.crm.api.schemas.schedule.ScheduleDisciplineSchema
import org.athletica.crm.api.schemas.schedule.ScheduleGroupSchema
import org.athletica.crm.api.schemas.schedule.ScheduleHallSchema
import org.athletica.crm.api.schemas.schedule.ScheduleListResponse
import org.athletica.crm.api.schemas.schedule.ScheduleSessionSchema
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.sessions.SessionStatus
import org.athletica.crm.storage.QueryBuilder
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/**
 * Реализация [ScheduleView] поверх PostgreSQL. Скоуп по филиалу — через группу занятия,
 * тренеры и дисциплины собираются JSONB-агрегатами, фильтры добавляются только заданные.
 * Занятия без группы отсекаются самим `JOIN groups`.
 */
class DbScheduleView : ScheduleView {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(query: ScheduleQuery): ScheduleListResponse {
        val sessions =
            tr
                .sql(
                    """
                    SELECT s.id, s.group_id, g.name AS group_name, s.date, s.start_time, s.end_time,
                           s.status::text AS status, h.id AS hall_id, h.name AS hall_name,
                           $COACHES_JSON AS coaches, $DISCIPLINES_JSON AS disciplines
                    FROM sessions s
                    JOIN groups g ON g.id = s.group_id AND g.org_id = s.org_id AND g.branch_id = :branchId
                    JOIN halls h ON h.id = s.hall_id
                    WHERE s.org_id = :orgId AND s.date BETWEEN :from AND :to${query.filters()}
                    ORDER BY s.date, s.start_time, s.id
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .bind("from", query.from)
                .bind("to", query.to)
                .bindFilters(query)
                .list { row -> row.toSession() }
        return ScheduleListResponse(sessions = sessions)
    }

    /** Условия фильтров, заданных в [ScheduleQuery]: внутри фильтра «или», между фильтрами «и». */
    private fun ScheduleQuery.filters(): String =
        buildString {
            if (hallIds != null) {
                append(" AND s.hall_id = ANY(:hallIds)")
            }
            if (employeeIds != null) {
                append(
                    " AND EXISTS (SELECT 1 FROM session_employees se" +
                        " WHERE se.session_id = s.id AND se.employee_id = ANY(:employeeIds))",
                )
            }
            if (disciplineIds != null) {
                append(
                    " AND EXISTS (SELECT 1 FROM group_disciplines gd" +
                        " WHERE gd.group_id = s.group_id AND gd.discipline_id = ANY(:disciplineIds))",
                )
            }
        }

    /** Привязывает параметры фильтров, объявленных в [ScheduleQuery.filters]. */
    private fun QueryBuilder.bindFilters(query: ScheduleQuery): QueryBuilder =
        let { q -> query.hallIds?.let { q.bind("hallIds", it) } ?: q }
            .let { q -> query.employeeIds?.let { q.bind("employeeIds", it) } ?: q }
            .let { q -> query.disciplineIds?.let { q.bind("disciplineIds", it) } ?: q }

    /** Собирает карточку занятия из строки выборки; ключ палитры — по первой дисциплине группы. */
    private fun Row.toSession(): ScheduleSessionSchema {
        val groupId = asUuid("group_id").toGroupId()
        val disciplines = Json.decodeFromString<List<ScheduleDisciplineSchema>>(asString("disciplines"))
        return ScheduleSessionSchema(
            id = asUuid("id").toSessionId(),
            group = ScheduleGroupSchema(id = groupId, name = asString("group_name")),
            date = asLocalDate("date"),
            startTime = asLocalTime("start_time"),
            endTime = asLocalTime("end_time"),
            hall = ScheduleHallSchema(id = asUuid("hall_id").toHallId(), name = asString("hall_name")),
            coaches = Json.decodeFromString<List<ScheduleCoachSchema>>(asString("coaches")),
            disciplines = disciplines,
            status = SessionStatus.valueOf(asString("status").uppercase()),
            colorKey = colorKeyFor(groupId, disciplines.firstOrNull()?.id),
        )
    }

    private companion object {
        /** Тренеры занятия с учётом переопределения состава, по имени. */
        val COACHES_JSON =
            """
            (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', e.id, 'name', e.name) ORDER BY e.name, e.id), '[]'::jsonb)
               FROM session_employees se
               JOIN employees e ON e.id = se.employee_id
              WHERE se.session_id = s.id)
            """.trimIndent()

        /** Дисциплины группы занятия, по названию; первая определяет запасной цвет карточки. */
        val DISCIPLINES_JSON =
            """
            (SELECT COALESCE(jsonb_agg(jsonb_build_object('id', d.id, 'name', d.name) ORDER BY d.name, d.id), '[]'::jsonb)
               FROM group_disciplines gd
               JOIN disciplines d ON d.id = gd.discipline_id
              WHERE gd.group_id = s.group_id)
            """.trimIndent()
    }
}
