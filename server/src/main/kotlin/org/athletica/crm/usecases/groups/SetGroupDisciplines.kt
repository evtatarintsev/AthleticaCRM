package org.athletica.crm.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.groups.SetGroupDisciplinesRequest
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.db.asLong
import org.athletica.crm.domain.audit.AuditLog
import org.athletica.crm.domain.audit.logUpdate
import org.athletica.crm.i18n.Messages

/**
 * Устанавливает список дисциплин для группы из [request].
 * Полностью заменяет текущий список: сначала удаляет все существующие привязки,
 * затем вставляет новые.
 * Проверяет, что группа принадлежит организации из [ctx].
 * Проверяет, что все дисциплины из [request] существуют в организации.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun setGroupDisciplines(request: SetGroupDisciplinesRequest): Either<CommonDomainError, Unit> =
    either {
        db
            .sql("SELECT id FROM groups WHERE id = :groupId AND org_id = :orgId")
            .bind("groupId", request.groupId)
            .bind("orgId", ctx.orgId)
            .firstOrNull { _ -> true }
            ?: raise(CommonDomainError("GROUP_NOT_FOUND", Messages.GroupNotFound.localize()))

        if (request.disciplineIds.isNotEmpty()) {
            val validCount =
                db
                    .sql("SELECT COUNT(*) as c FROM disciplines WHERE id = ANY(:ids) AND org_id = :orgId")
                    .bind("ids", request.disciplineIds)
                    .bind("orgId", ctx.orgId)
                    .firstOrNull { row -> row.asLong(0) }
                    ?: 0L
            if (validCount != request.disciplineIds.size.toLong()) {
                raise(CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()))
            }
        }

        try {
            db.transaction {
                sql("DELETE FROM group_disciplines WHERE group_id = :groupId")
                    .bind("groupId", request.groupId)
                    .execute()

                request.disciplineIds.forEach { disciplineId ->
                    sql("INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:groupId, :disciplineId)")
                        .bind("groupId", request.groupId)
                        .bind("disciplineId", disciplineId)
                        .execute()
                }
            }
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()))
        }

        val auditData = Json.encodeToString(request)
        audit.logUpdate("group", request.groupId, auditData)
    }
