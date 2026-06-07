package org.athletica.crm.domain.branch

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.BranchId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** R2DBC-реализация филиала. Все запросы фильтруются по `org_id`. */
data class DbBranch(
    override val id: BranchId,
    override val name: String,
) : Branch {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        try {
            tr.sql(
                """
                INSERT INTO branches (id, org_id, name)
                VALUES (:id, :orgId, :name)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name
                WHERE branches.org_id = :orgId
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("name", name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("BRANCH_ALREADY_EXISTS", Messages.BranchAlreadyExists.localize()))
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() {
        tr.sql("DELETE FROM branches WHERE id = :id AND org_id = :orgId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .execute()
    }

    override fun withNew(name: String): Branch = copy(name = name)
}
