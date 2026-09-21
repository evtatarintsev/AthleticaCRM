package org.athletica.crm.domain.groups

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.branchIdOrNull
import org.athletica.crm.core.entityids.DisciplineId
import org.athletica.crm.core.entityids.GroupId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.entityids.toDisciplineId
import org.athletica.crm.core.entityids.toEmployeeId
import org.athletica.crm.core.entityids.toGroupId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.Employee
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** Реализация [Groups] с доступом к PostgreSQL через R2DBC. */
class DbGroups : Groups {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(
        id: GroupId,
        name: String,
        disciplineIds: List<DisciplineId>,
        employees: List<Employee>,
    ): Group {
        try {
            tr
                .sql("INSERT INTO groups (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .bind("name", name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            if (e.message?.contains("uq_groups_org_name") == true) {
                raise(CommonDomainError("GROUP_NAME_ALREADY_EXISTS", Messages.GroupNameAlreadyExists.localize()))
            } else {
                raise(CommonDomainError("GROUP_ALREADY_EXISTS", Messages.GroupAlreadyExists.localize()))
            }
        }

        disciplineIds.forEach { disciplineId ->
            try {
                tr
                    .sql(
                        "INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:groupId, :disciplineId)",
                    )
                    .bind("groupId", id)
                    .bind("disciplineId", disciplineId)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()))
            }
        }
        employees.forEach { employee ->
            if (!employee.availableBranches.contains(ctx.branchId)) {
                raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))
            }
            try {
                tr
                    .sql(
                        "INSERT INTO group_employees (group_id, employee_id) VALUES (:groupId, :employeeId)",
                    ).bind("groupId", id)
                    .bind("employeeId", employee.id)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("EMPLOYEE_NOT_FOUND", Messages.EmployeeNotFound.localize()))
            }
        }
        return DbGroup(id, ctx.branchId, name, disciplineIds, employees.map { it.id })
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Group> {
        val branchId = ctx.branchIdOrNull
        val branchFilter = if (branchId != null) "AND g.branch_id = :branchId" else ""
        val groups =
            tr
                .sql(
                    """
                    SELECT g.id, g.branch_id, g.name FROM groups g
                    WHERE g.org_id = :orgId $branchFilter
                    ORDER BY g.name
                    """.trimIndent(),
                )
                .bind("orgId", ctx.orgId)
                .let { q -> if (branchId != null) q.bind("branchId", branchId) else q }
                .list { row ->
                    Triple(
                        row.asUuid("id").toGroupId(),
                        row.asUuid("branch_id").toBranchId(),
                        row.asString("name"),
                    )
                }

        if (groups.isEmpty()) {
            return emptyList()
        }

        val groupIds = groups.map { it.first.value }

        val disciplinesByGroup =
            tr
                .sql(
                    """
                    SELECT group_id, discipline_id
                    FROM group_disciplines
                    WHERE group_id = ANY(:ids)
                    """.trimIndent(),
                )
                .bind("ids", groupIds)
                .list { row ->
                    row.asUuid("group_id").toGroupId() to row.asUuid("discipline_id").toDisciplineId()
                }
                .groupBy({ it.first }, { it.second })

        val employeeIdsByGroup =
            tr
                .sql(
                    """
                    SELECT group_id, employee_id
                    FROM group_employees
                    WHERE group_id = ANY(:ids)
                    """.trimIndent(),
                ).bind("ids", groupIds)
                .list { row ->
                    row.asUuid("group_id").toGroupId() to row.asUuid("employee_id").toEmployeeId()
                }
                .groupBy({ it.first }, { it.second })

        return groups.map { (id, branchId, name) ->
            DbGroup(
                id = id,
                branchId = branchId,
                name = name,
                disciplines = disciplinesByGroup[id] ?: emptyList(),
                employeeIds = employeeIdsByGroup[id] ?: emptyList(),
            )
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
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

        return DbGroup(id, branchId, name, disciplines, employeeIds)
    }
}
