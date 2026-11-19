package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.NonBlankName
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toDisciplineId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.domain.sessions.DbScheduleSync
import org.athletica.crm.domain.sessions.ScheduleSync
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [Groups] с доступом к PostgreSQL через R2DBC. */
class DbGroups(private val sync: ScheduleSync = DbScheduleSync()) : Groups {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun new(
        id: GroupId,
        name: NonBlankName,
        disciplineIds: List<DisciplineId>,
        employees: List<Employee>,
    ): Group {
        tr
            .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .bind("name", name.value)
            .onViolation("uq_groups_org_name") { CommonDomainError("GROUP_NAME_ALREADY_EXISTS", Messages.GroupNameAlreadyExists.localize()) }
            .onViolation("groups_pkey") { CommonDomainError("GROUP_ALREADY_EXISTS", Messages.GroupAlreadyExists.localize()) }
            .execute()

        disciplineIds.forEach { disciplineId ->
            tr
                .sql(
                    "INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:groupId, :disciplineId)",
                )
                .bind("groupId", id)
                .bind("disciplineId", disciplineId)
                .onViolation("group_disciplines_discipline_id_fkey") { CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()) }
                .execute()
        }
        employees.forEach { employee ->
            ensure(employee.hasAccessTo(ctx.branchId)) { CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()) }
            tr
                .sql(
                    "INSERT INTO group_employees (group_id, employee_id) VALUES (:groupId, :employeeId)",
                ).bind("groupId", id)
                .bind("employeeId", employee.id)
                .onViolation("group_employees_employee_id_fkey") { CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()) }
                .execute()
        }
        return DbGroup(id, ctx.branchId, name.value, disciplineIds, employees.map { it.id }, sync)
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun byId(id: GroupId): Group {
        val (branchId, name) =
            tr
                .sql("SELECT branch_id, name FROM groups WHERE id = :id AND org_id = :orgId")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { row -> row.asUuid("branch_id").toBranchId() to row.asString("name") }
                ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))

        val disciplines =
            tr
                .sql("SELECT discipline_id FROM group_disciplines WHERE group_id = :groupId")
                .bind("groupId", id)
                .list { row -> row.asUuid("discipline_id").toDisciplineId() }

        val employeeIds =
            tr
                .sql("SELECT employee_id FROM group_employees WHERE group_id = :groupId")
                .bind("groupId", id)
                .list { row -> row.asUuid("employee_id").toEmployeeId() }

        return DbGroup(id, branchId, name, disciplines, employeeIds, sync)
    }
}
