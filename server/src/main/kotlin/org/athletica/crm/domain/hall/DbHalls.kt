package org.athletica.crm.domain.hall

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import org.athletica.crm.core.EmployeeRequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

/** R2DBC-реализация каталога залов. */
class DbHalls : Halls {
    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Hall> =
        tr.sql("SELECT h.id, h.name FROM halls h WHERE h.org_id = :orgId AND h.branch_id = :branchId ORDER BY h.name")
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .list {
                DbHall(id = it.asUuid("id").toHallId(), name = it.asString("name"))
            }

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun new(id: HallId, name: String): Hall = DbHall(id, name)

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: HallId): Hall =
        byIds(listOf(id)).singleOrNull()
            ?: raise(CommonDomainError("HALL_NOT_FOUND", Messages.HallNotFound.localize()))

    context(ctx: EmployeeRequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byIds(ids: List<HallId>): List<Hall> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) {
            return emptyList()
        }

        val result =
            tr.sql(
                """
                SELECT h.id, h.name FROM halls h
                WHERE h.id = ANY(:ids) AND h.org_id = :orgId AND h.branch_id = :branchId
                """.trimIndent(),
            )
                .bind("ids", distinctIds)
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .list {
                    DbHall(id = it.asUuid("id").toHallId(), name = it.asString("name"))
                }

        if (result.size != distinctIds.size) {
            raise(CommonDomainError("HALL_NOT_FOUND", Messages.HallNotFound.localize()))
        }
        return result
    }
}
