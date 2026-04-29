package org.athletica.crm.domain.branch

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.entityids.toBranchId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.uuid.toJavaUuid

class DbBranches : Branches {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Branch> =
        tr.sql("SELECT b.id, b.name FROM branches b WHERE b.org_id = :orgId ORDER BY b.name")
            .bind("orgId", ctx.orgId)
            .list {
                Branch(id = it.asUuid("id").toBranchId(), name = it.asString("name"))
            }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(branch: Branch) {
        try {
            tr
                .sql("INSERT INTO branches (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", branch.id.value)
                .bind("orgId", ctx.orgId)
                .bind("name", branch.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("BRANCH_ALREADY_EXISTS", Messages.BranchAlreadyExists.localize()))
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(branch: Branch) {
        val updatedRows =
            try {
                tr
                    .sql("UPDATE branches SET name = :name WHERE id = :id AND org_id = :orgId")
                    .bind("id", branch.id.value)
                    .bind("orgId", ctx.orgId)
                    .bind("name", branch.name)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(
                    CommonDomainError(
                        "BRANCH_NAME_ALREADY_EXISTS",
                        Messages.BranchAlreadyExists.localize(),
                    ),
                )
            }

        if (updatedRows == 0L) {
            raise(CommonDomainError("BRANCH_NOT_FOUND", Messages.BranchNotFound.localize()))
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(ids: List<BranchId>) {
        if (ids.isEmpty()) {
            return
        }

        val deleted =
            tr.sql("DELETE FROM branches WHERE id = ANY(:ids) AND org_id = :orgId RETURNING id, name")
                .bind("ids", ids.map { it.value.toJavaUuid() })
                .bind("orgId", ctx.orgId)
                .list { Branch(id = it.asUuid("id").toBranchId(), name = it.asString("name")) }

        deleted.forEach {
        }
    }
}
