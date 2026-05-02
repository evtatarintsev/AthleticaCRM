package org.athletica.crm.domain.sessions

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.SessionId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** Конкретная реализация [Session] на основе данных из PostgreSQL. */
class DbSession(
    override val id: SessionId,
    override val groupId: GroupId,
    override val groupName: String,
    override val date: LocalDate,
    override val startTime: LocalTime,
    override val endTime: LocalTime,
    override val hallId: HallId,
    override val status: String,
    override val isManual: Boolean,
    override val isRescheduled: Boolean,
    override val originDayOfWeek: String?,
    override val originStartTime: LocalTime?,
    override val originDate: LocalDate?,
    override val notes: String?,
    override val employeeIds: List<EmployeeId>,
    override val isEmployeeAssignmentOverridden: Boolean,
) : Session {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun cancel() {
        if (status != "scheduled") {
            raise(CommonDomainError("SESSION_CANNOT_CANCEL", "Можно отменить только запланированное занятие"))
        }
        tr
            .sql(
                """
                UPDATE sessions SET status = 'cancelled', cancelled_at = now()
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun reschedule(
        newDate: LocalDate,
        newStartTime: LocalTime,
        newEndTime: LocalTime,
        newHallId: HallId,
    ) {
        if (status != "scheduled") {
            raise(CommonDomainError("SESSION_CANNOT_RESCHEDULE", "Можно перенести только запланированное занятие"))
        }
        tr
            .sql(
                """
                UPDATE sessions
                SET date = :date, start_time = :startTime, end_time = :endTime, hall_id = :hallId, is_rescheduled = true
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("date", newDate.toJavaLocalDate())
            .bind("startTime", newStartTime.toString())
            .bind("endTime", newEndTime.toString())
            .bind("hallId", newHallId)
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun complete() {
        if (status != "scheduled") {
            raise(CommonDomainError("SESSION_CANNOT_COMPLETE", "Можно завершить только запланированное занятие"))
        }
        tr
            .sql(
                """
                UPDATE sessions SET status = 'completed', completed_at = now()
                WHERE id = :id AND org_id = :orgId
                """.trimIndent(),
            )
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun setEmployees(employeeIds: List<EmployeeId>) {
        if (status != "scheduled") {
            raise(CommonDomainError("SESSION_CANNOT_ASSIGN_EMPLOYEES", "Можно назначить преподавателей только для запланированного занятия"))
        }
        val branchId =
            tr
                .sql("SELECT branch_id FROM groups WHERE id = :groupId AND org_id = :orgId")
                .bind("groupId", groupId)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row -> row.get("branch_id", java.util.UUID::class.java) }
                ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
        tr
            .sql("DELETE FROM session_employees WHERE session_id = :sessionId")
            .bind("sessionId", id)
            .execute()
        employeeIds.distinct().forEach { employeeId ->
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
            tr
                .sql("INSERT INTO session_employees (session_id, employee_id) VALUES (:sessionId, :employeeId)")
                .bind("sessionId", id)
                .bind("employeeId", employeeId)
                .execute()
        }
        tr
            .sql("UPDATE sessions SET is_employee_assignment_overridden = true WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }
}
