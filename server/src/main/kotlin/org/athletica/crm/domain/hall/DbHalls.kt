package org.athletica.crm.domain.hall

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.entityids.HallId
import org.athletica.crm.core.entityids.toHallId
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid
import kotlin.uuid.toJavaUuid

class DbHalls : Halls {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Hall> =
        tr.sql("SELECT h.id, h.name FROM halls h WHERE h.org_id = :orgId AND h.branch_id = :branchId ORDER BY h.name")
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .list {
                Hall(id = it.asUuid("id").toHallId(), name = it.asString("name"))
            }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(hall: Hall) {
        try {
            tr
                .sql("INSERT INTO halls (id, org_id, branch_id, name) VALUES (:id, :orgId, :branchId, :name)")
                .bind("id", hall.id.value)
                .bind("orgId", ctx.orgId)
                .bind("branchId", ctx.branchId)
                .bind("name", hall.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("HALL_ALREADY_EXISTS", Messages.HallAlreadyExists.localize()))
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(hall: Hall) {
        val updatedRows =
            try {
                tr
                    .sql("UPDATE halls SET name = :name WHERE id = :id AND org_id = :orgId AND branch_id = :branchId")
                    .bind("id", hall.id.value)
                    .bind("orgId", ctx.orgId)
                    .bind("branchId", ctx.branchId)
                    .bind("name", hall.name)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(CommonDomainError("HALL_NAME_ALREADY_EXISTS", Messages.HallAlreadyExists.localize()))
            }

        if (updatedRows == 0L) {
            raise(CommonDomainError("HALL_NOT_FOUND", Messages.HallNotFound.localize()))
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(ids: List<HallId>) {
        if (ids.isEmpty()) {
            return
        }

        val hallUuids = ids.map { it.value.toJavaUuid() }
        val usedInSchedule =
            tr.sql("SELECT 1 FROM schedule_slots WHERE hall_id = ANY(:ids) AND org_id = :orgId LIMIT 1")
                .bind("ids", hallUuids)
                .bind("orgId", ctx.orgId)
                .firstOrNull { 1 } != null
        if (usedInSchedule) {
            raise(CommonDomainError("HALL_IN_USE", Messages.HallInUse.localize()))
        }

        val usedInSessions =
            tr.sql("SELECT 1 FROM sessions WHERE hall_id = ANY(:ids) AND org_id = :orgId LIMIT 1")
                .bind("ids", hallUuids)
                .bind("orgId", ctx.orgId)
                .firstOrNull { 1 } != null
        if (usedInSessions) {
            raise(CommonDomainError("HALL_IN_USE", Messages.HallInUse.localize()))
        }

        tr.sql("DELETE FROM halls WHERE id = ANY(:ids) AND org_id = :orgId AND branch_id = :branchId")
            .bind("ids", hallUuids)
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .execute()
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun byId(id: HallId): Hall =
        tr.sql("SELECT h.id, h.name FROM halls h WHERE h.id = :id AND h.org_id = :orgId AND h.branch_id = :branchId")
            .bind("id", id.value)
            .bind("orgId", ctx.orgId)
            .bind("branchId", ctx.branchId)
            .firstOrNull {
                Hall(id = it.asUuid("id").toHallId(), name = it.asString("name"))
            }
            ?: raise(CommonDomainError("HALL_NOT_FOUND", Messages.HallNotFound.localize()))
}
