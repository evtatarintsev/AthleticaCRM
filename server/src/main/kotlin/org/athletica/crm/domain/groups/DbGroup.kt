package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.EmployeeId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.errors.AppError
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** Конкретная реализация [Group] на основе данных из PostgreSQL. */
class DbGroup(
    override val id: GroupId,
    override val branchId: BranchId,
    override val name: String,
    override val disciplines: List<DisciplineId>,
    override val employeeIds: List<EmployeeId>,
) : Group {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun save() {
        val updatedRows =
            tr
                .sql("UPDATE groups SET name = :name WHERE id = :id AND org_id = :orgId")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("name", name)
                .onViolation("uq_groups_org_name") { CommonDomainError("GROUP_NAME_ALREADY_EXISTS", Messages.GroupNameAlreadyExists.localize()) }
                .execute()

        if (updatedRows == 0L) {
            raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))
        }

        tr
            .sql("DELETE FROM group_disciplines WHERE group_id = :groupId")
            .bind("groupId", id)
            .execute()

        disciplines.forEach { disciplineId ->
            tr
                .sql(
                    "INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:groupId, :disciplineId)",
                )
                .bind("groupId", id)
                .bind("disciplineId", disciplineId)
                .onViolation("group_disciplines_discipline_id_fkey") { CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()) }
                .execute()
        }

        tr
            .sql("DELETE FROM group_employees WHERE group_id = :groupId")
            .bind("groupId", id)
            .execute()

        employeeIds.forEach { employeeId ->
            tr
                .sql(
                    "INSERT INTO group_employees (group_id, employee_id) VALUES (:groupId, :employeeId)",
                ).bind("groupId", id)
                .bind("employeeId", employeeId)
                .onViolation("group_employees_employee_id_fkey") { CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()) }
                .execute()
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun withNewDisciplines(disciplines: List<DisciplineId>): Group = DbGroup(id, branchId, name, disciplines, employeeIds)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun withNewEmployees(employees: List<Employee>): Group {
        employees.forEach { employee ->
            if (!employee.availableBranches.contains(branchId)) {
                raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))
            }
        }
        return DbGroup(id, branchId, name, disciplines, employees.map { it.id })
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<AppError>)
    override suspend fun withNewName(name: String): Group = DbGroup(id, branchId, name, disciplines, employeeIds)
}
