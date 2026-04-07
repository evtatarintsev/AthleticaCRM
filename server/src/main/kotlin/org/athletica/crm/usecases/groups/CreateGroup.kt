package org.athletica.crm.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.GroupDiscipline
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logCreate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database
import org.athletica.crm.i18n.Messages
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

private val TIME_REGEX = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

context(ctx: RequestContext)
private fun ScheduleSlot.validate(): CommonDomainError? {
    if (!TIME_REGEX.matches(startAt)) {
        return CommonDomainError("INVALID_SCHEDULE_TIME", Messages.InvalidScheduleStartTime.localize(ctx.lang, startAt))
    }
    if (!TIME_REGEX.matches(endAt)) {
        return CommonDomainError("INVALID_SCHEDULE_TIME", Messages.InvalidScheduleEndTime.localize(ctx.lang, endAt))
    }
    if (endAt <= startAt) {
        return CommonDomainError(
            "INVALID_SCHEDULE_TIME",
            Messages.ScheduleEndBeforeStart.localize(ctx.lang, startAt, endAt),
        )
    }
    return null
}

/**
 * Создаёт новую группу в организации из [ctx] по данным [request].
 * В одной транзакции сохраняет группу, слоты расписания и привязки дисциплин.
 * Возвращает детали созданной группы, либо ошибку если группа уже существует
 * или одна из дисциплин не найдена.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun createGroup(request: GroupCreateRequest): Either<CommonDomainError, GroupDetailResponse> =
    either {
        request.schedule.forEach { slot ->
            slot.validate()?.let { raise(it) }
        }

        val disciplines =
            if (request.disciplineIds.isNotEmpty()) {
                db
                    .sql(
                        """
                        SELECT id, name FROM disciplines
                        WHERE id = ANY(:ids) AND org_id = :orgId
                        ORDER BY name
                        """.trimIndent(),
                    )
                    .bind("ids", request.disciplineIds.map { it.toJavaUuid() }.toTypedArray())
                    .bind("orgId", ctx.orgId.value)
                    .list { row, _ ->
                        GroupDiscipline(
                            id = row.get("id", java.util.UUID::class.java)!!.toKotlinUuid(),
                            name = row.get("name", String::class.java)!!,
                        )
                    }.also { result ->
                        if (result.size != request.disciplineIds.size) {
                            raise(CommonDomainError("DISCIPLINE_NOT_FOUND", Messages.DisciplineNotFound.localize()))
                        }
                    }
            } else {
                emptyList()
            }

        try {
            db.transaction {
                sql("INSERT INTO groups (id, org_id, name) VALUES (:id, :orgId, :name)")
                    .bind("id", request.id)
                    .bind("orgId", ctx.orgId.value)
                    .bind("name", request.name)
                    .execute()

                for (slot in request.schedule) {
                    sql(
                        """
                        INSERT INTO schedule_slots (org_id, group_id, day_of_week, start_time, end_time)
                        VALUES (:orgId, :groupId, :dayOfWeek::day_of_week, :startAt::time, :endAt::time)
                        """.trimIndent(),
                    )
                        .bind("orgId", ctx.orgId.value)
                        .bind("groupId", request.id)
                        .bind("dayOfWeek", slot.dayOfWeek.name)
                        .bind("startAt", slot.startAt)
                        .bind("endAt", slot.endAt)
                        .execute()
                }

                request.disciplineIds.forEach { disciplineId ->
                    sql("INSERT INTO group_disciplines (group_id, discipline_id) VALUES (:groupId, :disciplineId)")
                        .bind("groupId", request.id)
                        .bind("disciplineId", disciplineId)
                        .execute()
                }
            }
        } catch (e: R2dbcDataIntegrityViolationException) {
            if (e.message?.contains("uq_groups_org_name") == true) {
                raise(CommonDomainError("GROUP_NAME_ALREADY_EXISTS", Messages.GroupNameAlreadyExists.localize()))
            } else {
                raise(CommonDomainError("GROUP_ALREADY_EXISTS", Messages.GroupAlreadyExists.localize()))
            }
        }

        GroupDetailResponse(
            id = request.id,
            name = request.name,
            schedule = request.schedule,
            disciplines = disciplines,
        ).also { audit.logCreate(it) }
    }

/** Логирует создание группы: тип сущности `"group"`, данные — JSON-снапшот [result]. */
context(ctx: RequestContext)
fun AuditLog.logCreate(result: GroupDetailResponse) = logCreate("group", result.id, Json.encodeToString(result))
