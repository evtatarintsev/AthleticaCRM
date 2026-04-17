package org.athletica.crm.domain.discipline

import arrow.core.raise.context.Raise
import arrow.core.raise.context.raise
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.core.DisciplineId
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.core.errors.DomainError
import org.athletica.crm.core.toDisciplineId
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logCreate
import org.athletica.crm.domain.audit.logDelete
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.i18n.Messages
import org.athletica.crm.storage.Database
import org.athletica.crm.storage.asString
import org.athletica.crm.storage.asUuid

class DbDisciplines(private val db: Database, private val audit: AuditLog) : Disciplines {
    context(ctx: RequestContext, raise: Raise<DomainError>)
    override suspend fun list(): List<Discipline> =
        db.sql("SELECT s.id, s.name FROM disciplines s WHERE s.org_id = :orgId ORDER BY s.name")
            .bind("orgId", ctx.orgId)
            .list {
                Discipline(id = it.asUuid("id").toDisciplineId(), name = it.asString("name"))
            }

    context(ctx: RequestContext, raise: Raise<DomainError>)
    override suspend fun create(discipline: Discipline) {
        try {
            db
                .sql("INSERT INTO disciplines (id, org_id, name) VALUES (:id, :orgId, :name)")
                .bind("id", discipline.id)
                .bind("orgId", ctx.orgId)
                .bind("name", discipline.name)
                .execute()
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("DISCIPLINE_ALREADY_EXISTS", Messages.DisciplineAlreadyExists.localize()))
        }

        audit.logCreate("discipline", discipline.id, Json.encodeToString(discipline))
    }

    context(ctx: RequestContext, raise: Raise<DomainError>)
    override suspend fun update(discipline: Discipline) {
        val updatedRows =
            try {
                db
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

        audit.logUpdate("discipline", discipline.id, Json.encodeToString(discipline))
    }

    context(ctx: RequestContext, raise: Raise<DomainError>)
    override suspend fun delete(ids: List<DisciplineId>) {
        if (ids.isEmpty()) {
            return
        }

        val deleted =
            db.sql("DELETE FROM disciplines WHERE id = ANY(:ids) AND org_id = :orgId RETURNING id, name")
                .bind("ids", ids)
                .bind("orgId", ctx.orgId)
                .list { Discipline(id = it.asUuid("id").toDisciplineId(), name = it.asString("name")) }

        deleted.forEach {
            audit.logDelete("discipline", it.id, Json.encodeToString(it))
        }
    }
}
