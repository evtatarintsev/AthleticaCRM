package org.athletica.crm.domain.branch

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.domain.employees.EmployeeBranchAccess
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** R2DBC-реализация каталога филиалов. */
class DbBranches : Branches {
    context(ctx: EmployeeRequestContext, tr: Transaction)
    override suspend fun list(): List<Branch> =
        when (val access = ctx.availableBranches) {
            is EmployeeBranchAccess.All ->
                tr
                    .sql("SELECT id, name FROM branches WHERE org_id = :orgId ORDER BY name")
                    .bind("orgId", ctx.orgId)
                    .list { DbBranch(id = it.asUuid("id").toBranchId(), name = it.asString("name")) }

            is EmployeeBranchAccess.Selected ->
                if (access.ids.isEmpty()) {
                    emptyList()
                } else {
                    tr
                        .sql("SELECT id, name FROM branches WHERE org_id = :orgId AND id = ANY(:ids) ORDER BY name")
                        .bind("orgId", ctx.orgId)
                        .bind("ids", access.ids)
                        .list { DbBranch(id = it.asUuid("id").toBranchId(), name = it.asString("name")) }
                }
        }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: BranchId, name: String): Branch = DbBranch(id, name)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: BranchId): Branch =
        byIds(listOf(id)).singleOrNull()
            ?: raise(CommonDomainError("BRANCH_NOT_FOUND", Messages.BranchNotFound.localize()))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<BranchId>): List<Branch> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) {
            return emptyList()
        }

        val result =
            tr.sql("SELECT id, name FROM branches WHERE id = ANY(:ids) AND org_id = :orgId")
                .bind("ids", distinctIds)
                .bind("orgId", ctx.orgId)
                .list { DbBranch(id = it.asUuid("id").toBranchId(), name = it.asString("name")) }

        if (result.size != distinctIds.size) {
            raise(CommonDomainError("BRANCH_NOT_FOUND", Messages.BranchNotFound.localize()))
        }
        return result
    }
}
