package org.athletica.crm.domain.hall

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction

/** R2DBC-реализация зала. Все запросы ограничены `org_id` и текущим `branch_id`. */
data class DbHall(
    override val id: HallId,
    override val name: String,
) : Hall {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun save() {
        try {
            tr.sql(
                """
                INSERT INTO halls (id, org_id, branch_id, name)
                VALUES (:id, :orgId, :branchId, :name)
                ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name
                WHERE halls.org_id = :orgId AND halls.branch_id = :branchId
                """.trimIndent(),
            )
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .bind("name", name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("HALL_ALREADY_EXISTS", Messages.HallAlreadyExists.localize()))
        }
    }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete() {
        val usedInSchedule =
            tr.sql("SELECT 1 FROM schedule_slots WHERE hall_id = :id AND org_id = :orgId LIMIT 1")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { 1 } != null
        if (usedInSchedule) {
            raise(CommonDomainError("HALL_IN_USE", Messages.HallInUse.localize()))
        }

        val usedInSessions =
            tr.sql("SELECT 1 FROM sessions WHERE hall_id = :id AND org_id = :orgId LIMIT 1")
                .bind("id", id)
                .bind("orgId", ctx.orgId)
                .firstOrNull { 1 } != null
        if (usedInSessions) {
            raise(CommonDomainError("HALL_IN_USE", Messages.HallInUse.localize()))
        }

        tr.sql("DELETE FROM halls WHERE id = :id AND org_id = :orgId AND branch_id = :branchId")
            .bind("id", id)
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .execute()
    }

    override fun withNew(name: String): Hall = copy(name = name)
}
