package org.athletica.crm.domain.discipline

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import org.athletica.crm.core.DisciplineId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toDisciplineId
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Transaction
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

class DbDisciplines : Disciplines {
    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun list(): List<Discipline> =
        tr.sql("SELECT s.id, s.name FROM disciplines s WHERE s.org_id = :orgId ORDER BY s.name")
            .bind("orgId", ctx.orgId)
            .list {
                Discipline(id = it.asUuid("id").toDisciplineId(), name = it.asString("name"))
            }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun create(discipline: Discipline) {
        try {
            tr
                .sql("INSERT INTO disciplines (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", discipline.id)
                .bind("orgId", ctx.orgId)
                .bind("name", discipline.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("DISCIPLINE_ALREADY_EXISTS", Messages.DisciplineAlreadyExists.localize()))
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun update(discipline: Discipline) {
        val updatedRows =
            try {
                tr
                    .sql("UPDATE disciplines SET name = :name WHERE id = :id AND org_id = :orgId")
                    .bind("id", discipline.id)
                    .bind("orgId", ctx.orgId)
                    .bind("name", discipline.name)
                    .execute()
            } catch (e: R2dbcDataIntegrityViolationException) {
                raise(
                    CommonDomainError(
                        "DISCIPLINE_NAME_ALREADY_EXISTS",
                        Messages.DisciplineAlreadyExists.localize(),
                    ),
                )
            }

        if (updatedRows == 0L) {
            raise(CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()))
        }
    }

    context(ctx: RequestContext, tr: Transaction, raise: Raise<DomainError>)
    override suspend fun delete(ids: List<DisciplineId>) {
        if (ids.isEmpty()) {
            return
        }

        val deleted =
            tr.sql("DELETE FROM disciplines WHERE id = ANY(:ids) AND org_id = :orgId RETURNING id, name")
                .bind("ids", ids)
                .bind("orgId", ctx.orgId)
                .list { Discipline(id = it.asUuid("id").toDisciplineId(), name = it.asString("name")) }

        deleted.forEach {
        }
    }
}
