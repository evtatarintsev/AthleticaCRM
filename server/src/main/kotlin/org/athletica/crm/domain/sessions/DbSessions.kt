package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.entityids.toSessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asBoolean
import org.athletica.crm.storage.asLocalDate
import org.athletica.crm.storage.asLocalDateOrNull
import org.athletica.crm.storage.asLocalTime
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asStringOrNull
import org.athletica.crm.storage.asUuid

/** Реализация [Sessions] с доступом к PostgreSQL через R2DBC. */
class DbSessions : Sessions {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: SessionId,
        groupId: GroupId,
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        hallId: HallId,
        notes: String?,
        employeeIds: List<EmployeeId>,
        originDayOfWeek: String?,
        originStartTime: LocalTime?,
        originDate: LocalDate?,
    ): Session? =
        try {
            val branchId =
                tr
                    .sql("SELECT branch_id FROM groups WHERE id = :groupId AND org_id = :orgId")
                    .bind("groupId", groupId)
                    .bind("orgId", ctx.orgId)
                    .firstOrNull { row -> row.get("branch_id", java.util.UUID::class.java) }
                    ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
            employeeIds.distinct().forEach { employeeId ->
                validateEmployeeForBranch(employeeId, branchId)
            }
            tr
                .sql(
                    """
                    INSERT INTO sessions (
                        id, org_id, group_id, date, start_time, end_time, hall_id, notes,
                        is_manual, origin_day_of_week, origin_start_time, origin_date
                    ) VALUES (
                        :id, :orgId, :groupId, :date, :startTime::time, :endTime::time, :hallId, :notes,
                        :isManual, :originDayOfWeek, :originStartTime::time, :originDate
                    )
                    ON CONFLICT (group_id, origin_day_of_week, origin_start_time, origin_date)
                    WHERE origin_day_of_week IS NOT NULL
                    DO NOTHING
                    """.trimIndent(),
                )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("groupId", groupId)
                .bind("date", date.toJavaLocalDate())
                .bind("startTime", startTime.toString())
                .bind("endTime", endTime.toString())
                .bind("hallId", hallId)
                .bind("notes", notes)
                .bind("isManual", originDayOfWeek == null)
                .bind("originDayOfWeek", originDayOfWeek)
                .bind("originStartTime", originStartTime?.toString())
                .bind("originDate", originDate?.toJavaLocalDate())
                .execute()
            val session = byIdOrNull(id)
            session?.let {
                employeeIds.distinct().forEach { employeeId ->
                    tr
                        .sql("INSERT INTO session_employees (session_id, employee_id) VALUES (:sessionId, :employeeId)")
                        .bind("sessionId", id)
                        .bind("employeeId", employeeId)
                        .execute()
                }
            }
            byIdOrNull(id)
        } catch (e: R2dbcDataIntegrityViolationException) {
            null
        }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(
        groupId: GroupId,
        from: LocalDate,
        to: LocalDate,
    ): List<Session> {
        val rows =
            tr
                .sql(
                    """
                    SELECT s.id, s.group_id, g.name as group_name, s.date, s.start_time, s.end_time, s.hall_id,
                           s.status, s.is_manual, s.is_rescheduled, s.origin_day_of_week,
                           s.origin_start_time, s.origin_date, s.notes, s.is_employee_assignment_overridden
                    FROM sessions s
                    JOIN groups g ON g.id = s.group_id
                    WHERE s.org_id = :orgId AND s.group_id = :groupId
                      AND s.date >= :from AND s.date <= :to
                    ORDER BY s.date, s.start_time
                    """.trimIndent(),
                ).bind("orgId", ctx.orgId)
                .bind("groupId", groupId)
                .bind("from", from.toJavaLocalDate())
                .bind("to", to.toJavaLocalDate())
                .list { row -> row.toSessionRow() }
        if (rows.isEmpty()) {
            return emptyList()
        }
        val sessionIds = rows.map { it.id }
        val employeeIdsBySession = loadEmployeeIds(sessionIds)
        return rows.map { row ->
            row.toSession(employeeIdsBySession[row.id] ?: emptyList())
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun listAll(
        from: LocalDate,
        to: LocalDate,
    ): List<Session> {
        val rows =
            tr
                .sql(
                    """
                    SELECT s.id, s.group_id, g.name as group_name, s.date, s.start_time, s.end_time, s.hall_id,
                           s.status, s.is_manual, s.is_rescheduled, s.origin_day_of_week,
                           s.origin_start_time, s.origin_date, s.notes, s.is_employee_assignment_overridden
                    FROM sessions s
                    JOIN groups g ON g.id = s.group_id
                    WHERE s.org_id = :orgId
                      AND s.date >= :from AND s.date <= :to
                    ORDER BY s.date, s.start_time
                    """.trimIndent(),
                ).bind("orgId", ctx.orgId)
                .bind("from", from.toJavaLocalDate())
                .bind("to", to.toJavaLocalDate())
                .list { row -> row.toSessionRow() }
        if (rows.isEmpty()) {
            return emptyList()
        }
        val sessionIds = rows.map { it.id }
        val employeeIdsBySession = loadEmployeeIds(sessionIds)
        return rows.map { row ->
            row.toSession(employeeIdsBySession[row.id] ?: emptyList())
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: SessionId): Session =
        byIdOrNull(id)
            ?: raise(CommonDomainError("SESSION_NOT_FOUND", "Занятие не найдено"))

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun futureScheduledBySlot(
        groupId: GroupId,
        dayOfWeek: String,
        startTime: LocalTime,
        from: LocalDate,
    ): List<Session> {
        val rows =
            tr
                .sql(
                    """
                    SELECT s.id, s.group_id, g.name as group_name, s.date, s.start_time, s.end_time, s.hall_id,
                           s.status, s.is_manual, s.is_rescheduled, s.origin_day_of_week,
                           s.origin_start_time, s.origin_date, s.notes, s.is_employee_assignment_overridden
                    FROM sessions s
                    JOIN groups g ON g.id = s.group_id
                    WHERE s.org_id = :orgId AND s.group_id = :groupId
                      AND s.origin_day_of_week = :dayOfWeek
                      AND s.origin_start_time = :startTime::time
                      AND s.status = 'scheduled'
                      AND s.date >= :from
                      AND s.is_rescheduled = false
                    ORDER BY s.date
                    """.trimIndent(),
                ).bind("orgId", ctx.orgId)
                .bind("groupId", groupId)
                .bind("dayOfWeek", dayOfWeek)
                .bind("startTime", startTime.toString())
                .bind("from", from.toJavaLocalDate())
                .list { row -> row.toSessionRow() }
        if (rows.isEmpty()) {
            return emptyList()
        }
        val sessionIds = rows.map { it.id }
        val employeeIdsBySession = loadEmployeeIds(sessionIds)
        return rows.map { row ->
            row.toSession(employeeIdsBySession[row.id] ?: emptyList())
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun syncFutureEmployeesFromGroup(
        groupId: GroupId,
        employeeIds: List<EmployeeId>,
        from: LocalDate,
    ) {
        val sessionIds =
            tr
                .sql(
                    """
                    SELECT id
                    FROM sessions
                    WHERE org_id = :orgId
                      AND group_id = :groupId
                      AND status = 'scheduled'
                      AND date >= :from
                      AND is_employee_assignment_overridden = false
                    """.trimIndent(),
                ).bind("orgId", ctx.orgId)
                .bind("groupId", groupId)
                .bind("from", from.toJavaLocalDate())
                .list { row -> row.asUuid("id").toSessionId() }
        if (sessionIds.isEmpty()) {
            return
        }
        tr
            .sql("DELETE FROM session_employees WHERE session_id = ANY(:sessionIds)")
            .bind("sessionIds", sessionIds.map { it.value })
            .execute()
        sessionIds.forEach { sessionId ->
            employeeIds.distinct().forEach { employeeId ->
                tr
                    .sql("INSERT INTO session_employees (session_id, employee_id) VALUES (:sessionId, :employeeId)")
                    .bind("sessionId", sessionId)
                    .bind("employeeId", employeeId)
                    .execute()
            }
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun byIdOrNull(id: SessionId): Session? =
        tr
            .sql(
                """
                SELECT s.id, s.group_id, g.name as group_name, s.date, s.start_time, s.end_time, s.hall_id,
                       s.status, s.is_manual, s.is_rescheduled, s.origin_day_of_week,
                       s.origin_start_time, s.origin_date, s.notes, s.is_employee_assignment_overridden
                FROM sessions s
                JOIN groups g ON g.id = s.group_id
                WHERE s.id = :id AND s.org_id = :orgId
                """.trimIndent(),
            ).bind("id", id)
            .bind("orgId", ctx.orgId)
            .firstOrNull { row -> row.toSessionRow() }
            ?.let { row ->
                val employeeIdsBySession = loadEmployeeIds(listOf(id))
                row.toSession(employeeIdsBySession[id] ?: emptyList())
            }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun loadEmployeeIds(sessionIds: List<SessionId>): Map<SessionId, List<EmployeeId>> =
        tr
            .sql(
                """
                SELECT session_id, employee_id
                FROM session_employees
                WHERE session_id = ANY(:sessionIds)
                """.trimIndent(),
            ).bind("sessionIds", sessionIds.map { it.value })
            .list { row -> row.asUuid("session_id").toSessionId() to row.asUuid("employee_id").toEmployeeId() }
            .groupBy({ it.first }, { it.second })

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    private suspend fun validateEmployeeForBranch(employeeId: EmployeeId, branchId: java.util.UUID) {
        val allBranchesAccess =
            tr
                .sql("SELECT all_branches_access FROM employees WHERE id = :employeeId AND org_id = :orgId AND is_active = true")
                .bind("employeeId", employeeId)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row -> row.get("all_branches_access", java.lang.Boolean::class.java) ?: false }
                ?: raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))
        if (allBranchesAccess != true) {
            val branchAllowed =
                tr
                    .sql("SELECT 1 FROM employee_branches WHERE employee_id = :employeeId AND branch_id = :branchId")
                    .bind("employeeId", employeeId)
                    .bind("branchId", branchId)
                    .firstOrNull { 1 } != null
            if (!branchAllowed) {
                raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))
            }
        }
    }
}

private data class SessionRow(
    val id: SessionId,
    val groupId: GroupId,
    val groupName: String,
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val hallId: HallId,
    val status: String,
    val isManual: Boolean,
    val isRescheduled: Boolean,
    val originDayOfWeek: String?,
    val originStartTime: LocalTime?,
    val originDate: LocalDate?,
    val notes: String?,
    val isEmployeeAssignmentOverridden: Boolean,
)

private fun io.r2dbc.spi.Row.toSessionRow(): SessionRow =
    SessionRow(
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
        originDayOfWeek = asStringOrNull("origin_day_of_week"),
        originStartTime =
            get("origin_start_time", java.time.LocalTime::class.java)?.let {
                kotlinx.datetime.LocalTime(it.hour, it.minute, it.second)
            },
        originDate = asLocalDateOrNull("origin_date"),
        notes = asStringOrNull("notes"),
        isEmployeeAssignmentOverridden = asBoolean("is_employee_assignment_overridden"),
    )

private fun SessionRow.toSession(employeeIds: List<EmployeeId>): DbSession =
    DbSession(
        id = id,
        groupId = groupId,
        groupName = groupName,
        date = date,
        startTime = startTime,
        endTime = endTime,
        hallId = hallId,
        status = status,
        isManual = isManual,
        isRescheduled = isRescheduled,
        originDayOfWeek = originDayOfWeek,
        originStartTime = originStartTime,
        originDate = originDate,
        notes = notes,
        employeeIds = employeeIds,
        isEmployeeAssignmentOverridden = isEmployeeAssignmentOverridden,
    )
