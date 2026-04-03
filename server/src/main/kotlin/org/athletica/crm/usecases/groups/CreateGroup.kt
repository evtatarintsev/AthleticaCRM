package org.athletica.crm.usecases.groups

import arrow.core.Either
import arrow.core.raise.either
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import kotlinx.serialization.json.Json
import org.athletica.crm.api.schemas.groups.GroupCreateRequest
import org.athletica.crm.api.schemas.groups.GroupDetailResponse
import org.athletica.crm.api.schemas.groups.ScheduleSlot
import org.athletica.crm.audit.AuditLog
import org.athletica.crm.audit.logCreate
import org.athletica.crm.core.RequestContext
import org.athletica.crm.core.errors.CommonDomainError
import org.athletica.crm.db.Database

private val TIME_REGEX = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")

private fun ScheduleSlot.validate(): CommonDomainError? {
    if (!TIME_REGEX.matches(startAt)) {
        return CommonDomainError("INVALID_SCHEDULE_TIME", "Некорректное время начала слота: \"$startAt\"")
    }
    if (!TIME_REGEX.matches(endAt)) {
        return CommonDomainError("INVALID_SCHEDULE_TIME", "Некорректное время окончания слота: \"$endAt\"")
    }
    if (endAt <= startAt) {
        return CommonDomainError(
            "INVALID_SCHEDULE_TIME",
            "Время окончания должно быть позже времени начала: $startAt – $endAt",
        )
    }
    return null
}

/**
 * Создаёт новую группу в организации из [ctx] по данным [request].
 * В одной транзакции сохраняет группу и все слоты расписания.
 * Возвращает детали созданной группы, либо ошибку если группа уже существует.
 */
context(db: Database, ctx: RequestContext, audit: AuditLog)
suspend fun createGroup(request: GroupCreateRequest): Either<CommonDomainError, GroupDetailResponse> =
    either {
        request.schedule.forEach { slot ->
            slot.validate()?.let { raise(it) }
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
            }
        } catch (e: R2dbcDataIntegrityViolationException) {
            raise(CommonDomainError("GROUP_ALREADY_EXISTS", "Группа с таким идентификатором уже существует"))
        }

        GroupDetailResponse(
            id = request.id,
            name = request.name,
            schedule = request.schedule,
        ).also { audit.logCreate(it) }
    }

/** Логирует создание группы: тип сущности `"group"`, данные — JSON-снапшот [result]. */
context(ctx: RequestContext)
fun AuditLog.logCreate(result: GroupDetailResponse) = logCreate("group", result.id, Json.encodeToString(result))
